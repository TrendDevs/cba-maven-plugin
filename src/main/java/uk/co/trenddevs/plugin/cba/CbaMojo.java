package uk.co.trenddevs.plugin.cba;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.archiver.PomPropertiesUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.apache.maven.shared.osgi.DefaultMaven2OsgiConverter;
import org.apache.maven.shared.osgi.Maven2OsgiConverter;
import aQute.lib.osgi.Analyzer;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds Component Bundle Archive (cba) files for Websphere Application Server.
 *
 * @version $Id: $
 * @goal cba
 * @phase package
 * @requiresDependencyResolution test
 */
public class CbaMojo  extends AbstractMojo  {

	public static final String COMPOSITE_BUNDLE_MF_URI = "META-INF/COMPOSITEBUNDLE.MF";

    private static final String[] DEFAULT_INCLUDES = {"**/**"};

    /**
     * CompositeBundle.MF manifest headers
     *
     * Please see <a href="https://www-01.ibm.com/support/knowledgecenter/#!/was_beta/com.ibm.websphere.wdt.doc/topics/ccba.htm">IBM documentation</a> for further detail
     *
     */
    public static final String MANIFEST_VERSION = "Manifest-Version";
    public static final String COMPOSITE_BUNDLE_MANIFEST_VERSION = "CompositeBundle-ManifestVersion";
    public static final String BUNDLE_NAME = "Bundle-Name";
    public static final String BUNDLE_DESCRIPTION = "Bundle-Description";
    public static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";
    public static final String BUNDLE_VERSION = "Bundle-Version";
    public static final String COMPOSITE_BUNDLE_CONTENT = "CompositeBundle-Content";
    public static final String COMPOSITE_BUNDLE_EXPORT_SERVICE = "CompositeBundle-ExportService";
    public static final String COMPOSITE_BUNDLE_IMPORT_SERVICE = "CompositeBundle-ImportService";
    public static final String COMPOSITE_BUNDLE_EXPORT_PACKAGE = "Export-Package";
    public static final String COMPOSITE_BUNDLE_IMPORT_PACKAGE = "Import-Package";


    /**
     * Coverter for maven pom values to OSGi manifest values (pulled in from the maven-bundle-plugin)
     */
    private Maven2OsgiConverter maven2OsgiConverter = new DefaultMaven2OsgiConverter();
    
    /**
     * Directory with extra files to include in the cba.
     *
     * @parameter property="${basedir}/src/main/cba"
     * @required
     */
    private File cbaSourceDirectory;

    /**
     * The location of the COMPOSITEBUNDLE.MF file to be used within the cba file.
     *
     * @parameter property="${basedir}/src/main/cba/META-INF/COMPOSITEBUNDLE.MF"
     */
    private File compositeBundleManifestFile;

    /**
     * Should generated jar file be included in the cba file ; default is true.
     * @parameter
     */
    private Boolean includeJar = Boolean.TRUE;

    /**
     * Work directory for temporary files generated during plugin execution.
     *
     * @parameter property="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private String workDirectory;

    /**
     * Output directory for the cba.
     *
     * @parameter property="${project.build.directory}"
     * @required
     */
    private String outputDirectory;

    /**
     * The name of the cba file to generate.
     *
     * @parameter alias="cbaName" property="${project.build.finalName}"
     * @required
     */
    private String finalName;

    /**
     * The maven project.
     *
     * @parameter property="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The Jar archiver.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="zip"
     * @required
     */
    private ZipArchiver zipArchiver;

    /**
     * Whether to generate a manifest based on maven configuration.
     *
     * @parameter property="${generateManifest}" default-value="false"
     */
    private boolean generateManifest;

    /**
     * Configuration for the plugin.
     *
     * @parameter
     */
    private Map instructions = new LinkedHashMap();;    
    
    /**
     * Adding pom.xml and pom.properties to the archive.
     *
     * @parameter property="${addMavenDescriptor}" default-value="true"
     */
    private boolean addMavenDescriptor;

    /**
     * Include or not empty directories
     *
     * @parameter property="${includeEmptyDirs}" default-value="true"
     */
    private boolean includeEmptyDirs;

    /**
     * Whether creating the archive should be forced.
     *
     * @parameter property="${forceCreation}" default-value="false"
     */
    private boolean forceCreation;

    /**
     * Whether to follow transitive dependencies or use explicit dependencies.
     *
     * @parameter property="${useTransitiveDependencies}" default-value="false"
     */
    private boolean useTransitiveDependencies;

    /**
     * Define which bundles to include in the archive.
     *   none - no bundles are included 
     *   applicationContent - direct dependencies go into the content
     *   all - direct and transitive dependencies go into the content 
     *
     * @parameter property="${archiveContent}" default-value="applicationContent"
     */
    private String archiveContent;

    private File buildDir;


    public void execute() throws MojoExecutionException {

        getLog().debug( " ======= CbaMojo settings =======" );
        getLog().debug( "cbaSourceDirectory[" + cbaSourceDirectory + "]" );
        getLog().debug( "compositeBundleManifestFile[" + compositeBundleManifestFile + "]" );
        getLog().debug( "workDirectory[" + workDirectory + "]" );
        getLog().debug( "outputDirectory[" + outputDirectory + "]" );
        getLog().debug( "finalName[" + finalName + "]" );
        getLog().debug( "generateManifest[" + generateManifest + "]" );

        if (archiveContent == null) {
        	archiveContent = new String("applicationContent");
        }
        
        getLog().debug( "archiveContent[" + archiveContent + "]" );        
        getLog().info( "archiveContent[" + archiveContent + "]" );        
        
        zipArchiver.setIncludeEmptyDirs( includeEmptyDirs );
        zipArchiver.setCompress( true );
        zipArchiver.setForced( forceCreation );

        // Include project artifact
        try {
            if (includeJar.booleanValue()) {
                File generatedJarFile = new File( outputDirectory, finalName + ".jar" );
                if (generatedJarFile.exists()) {
                    getLog().info( "Including generated jar file["+generatedJarFile.getName()+"]");
                    zipArchiver.addFile(generatedJarFile, finalName + ".jar");
                }
            }
        }
        catch ( ArchiverException e ) {
            throw new MojoExecutionException( "Error adding generated Jar file", e );
        }

        // Copy dependencies
        try {

            Set<Artifact> artifacts = null;
            if (useTransitiveDependencies) {

                // if use transitive is set (i.e. true) then we need to make sure archiveContent does not contradict (i.e. is set to the same compatible value or is the default).
            	if ("none".equals(archiveContent)) {
                    throw new MojoExecutionException("<useTransitiveDependencies/> and <archiveContent/> incompatibly configured.  <useTransitiveDependencies/> is deprecated in favor of <archiveContent/>." );            		
            	}
            	else {
                    artifacts = project.getArtifacts();            		
            	}

            } else {
            	// check that archiveContent is compatible
            	if ("applicationContent".equals(archiveContent)) {
                    artifacts = project.getDependencyArtifacts();            		
            	}
            	else {
                	// the only remaining options should be applicationContent="none"
                    getLog().info("archiveContent=none: application arvhive will not contain any bundles.");            		
            	}
            }
            if (artifacts != null) {
                for (Artifact artifact : artifacts) {
                    ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
                    if (!artifact.isOptional() && filter.include(artifact)) {
                        getLog().info("Copying artifact[" + artifact.getGroupId() + ", " + artifact.getId() + ", " +
                                artifact.getScope() + "]");
                        zipArchiver.addFile(artifact.getFile(), artifact.getArtifactId() + "-" + artifact.getVersion() + "." + (artifact.getType() == null ? "jar" : artifact.getType()));
                    }
                }            	
            }
        }
        catch ( ArchiverException e ) {
            throw new MojoExecutionException( "Error copying EBA dependencies", e );
        }

        // Copy source files
        try
        {
            File cbaSourceDir =  cbaSourceDirectory;
            if ( cbaSourceDir.exists() )
            {
                getLog().info( "Copy cba resources to " + getBuildDir().getAbsolutePath() );

                DirectoryScanner scanner = new DirectoryScanner();
                scanner.setBasedir( cbaSourceDir.getAbsolutePath() );
                scanner.setIncludes( DEFAULT_INCLUDES );
                scanner.addDefaultExcludes();
                scanner.scan();

                String[] dirs = scanner.getIncludedDirectories();

                for ( int j = 0; j < dirs.length; j++ ) {
                    new File( getBuildDir(), dirs[j] ).mkdirs();
                }

                String[] files = scanner.getIncludedFiles();

                for ( int j = 0; j < files.length; j++ )  {
                    File targetFile = new File( getBuildDir(), files[j] );
                    targetFile.getParentFile().mkdirs();
                    File file = new File( cbaSourceDir, files[j] );
                    FileUtils.copyFileToDirectory( file, targetFile.getParentFile() );
                }
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Error copying CBA resources", e );
        }

        // Include custom manifest if necessary
        try
        {
            if (!generateManifest) {
            	includeCustomApplicationManifestFile();
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error copying COMPONENTBUNDLE.MF file", e );
        }

		// Generate application manifest if requested
		if (generateManifest) {
			String fileName = new String(getBuildDir() + "/" + COMPOSITE_BUNDLE_MF_URI);
			File appMfFile = new File(fileName);

			try {
				// Delete any old manifest
				if (appMfFile.exists()) {
					FileUtils.fileDelete(fileName);
				}

				appMfFile.getParentFile().mkdirs();
				if (appMfFile.createNewFile()) {
                    writeCompositeBundleManifest(fileName);
				}
			} catch (IOException e) {
				throw new MojoExecutionException("Error generating APPLICATION.MF file: " + fileName, e);
			}
		}
        
        // Check if connector deployment descriptor is there
        File ddFile = new File( getBuildDir(), COMPOSITE_BUNDLE_MF_URI);
        if ( !ddFile.exists() ) {
            getLog().warn("Application manifest: " + ddFile.getAbsolutePath() + " does not exist." );
        }

        try  {
            if (addMavenDescriptor) {
                if (project.getArtifact().isSnapshot()) {
                    project.setVersion(project.getArtifact().getVersion());
                }

                String groupId = project.getGroupId();

                String artifactId = project.getArtifactId();

                zipArchiver.addFile(project.getFile(), "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml");
                PomPropertiesUtil pomPropertiesUtil = new PomPropertiesUtil();
                File dir = new File(project.getBuild().getDirectory(), "maven-zip-plugin");
                File pomPropertiesFile = new File(dir, "pom.properties");
                pomPropertiesUtil.createPomProperties(project, zipArchiver, pomPropertiesFile, forceCreation);
            }

            File cbaFile = new File( outputDirectory, finalName + ".cba" );
            zipArchiver.setDestFile(cbaFile);

            File buildDir = getBuildDir();
            if (buildDir.isDirectory()) {
                zipArchiver.addDirectory(buildDir);
            }

            zipArchiver.createArchive();

            project.getArtifact().setFile( cbaFile );

        } catch ( Exception e )  {
            throw new MojoExecutionException( "Error assembling eba", e );
        }

    }


    /**
     * Write CompositeBundle manifest file
     * @param fileName
     * @throws MojoExecutionException
     */
	private void writeCompositeBundleManifest(String fileName) throws MojoExecutionException {
		try {

			// TODO: add support for dependency version ranges. Need to pick them up from the pom and convert them to OSGi version ranges.
			FileUtils.fileAppend(fileName, MANIFEST_VERSION + ": " + "1" + "\n");
			FileUtils.fileAppend(fileName, COMPOSITE_BUNDLE_MANIFEST_VERSION + ": " + "1" + "\n");
			FileUtils.fileAppend(fileName, BUNDLE_SYMBOLIC_NAME + ": " + getApplicationSymbolicName(project.getArtifact()) + "\n");
			FileUtils.fileAppend(fileName, BUNDLE_VERSION + ": " + getBundleVersion() + "\n");
			FileUtils.fileAppend(fileName, BUNDLE_NAME + ": " + project.getName() + "\n");
			FileUtils.fileAppend(fileName, BUNDLE_DESCRIPTION + ": " + project.getDescription() + "\n");

			// Write the APPLICATION-CONTENT
			// TODO: check that the dependencies are bundles (currently, the converter will throw an exception)
			Set<Artifact> artifacts = useTransitiveDependencies ? selectArtifacts(project.getArtifacts()) : selectArtifacts(project.getDependencyArtifacts());
			Iterator<Artifact> iter = artifacts.iterator();

			FileUtils.fileAppend(fileName, COMPOSITE_BUNDLE_CONTENT + ": ");
			if (iter.hasNext()) {
				Artifact artifact = iter.next();
				FileUtils.fileAppend(fileName, maven2OsgiConverter.getBundleSymbolicName(artifact) + ";version=\"" + Analyzer.cleanupVersion(artifact.getVersion()) + "\"");
			}

			while (iter.hasNext()) {
				Artifact artifact = iter.next();
				FileUtils.fileAppend(fileName, ",\n " + maven2OsgiConverter.getBundleSymbolicName(artifact) + ";version=\"" + Analyzer.cleanupVersion(artifact.getVersion()) + "\"");
			}

			FileUtils.fileAppend(fileName, "\n");

			if (instructions.containsKey(COMPOSITE_BUNDLE_EXPORT_SERVICE))
				FileUtils.fileAppend(fileName, COMPOSITE_BUNDLE_EXPORT_SERVICE + ": " + instructions.get(COMPOSITE_BUNDLE_EXPORT_SERVICE) + "\n");
			if (instructions.containsKey(COMPOSITE_BUNDLE_IMPORT_SERVICE))
				FileUtils.fileAppend(fileName, COMPOSITE_BUNDLE_IMPORT_SERVICE + ": " + instructions.get(COMPOSITE_BUNDLE_IMPORT_SERVICE) + "\n");

            if (instructions.containsKey(COMPOSITE_BUNDLE_EXPORT_PACKAGE))
                FileUtils.fileAppend(fileName, COMPOSITE_BUNDLE_EXPORT_PACKAGE + ": " + instructions.get(COMPOSITE_BUNDLE_EXPORT_PACKAGE) + "\n");
            if (instructions.containsKey(COMPOSITE_BUNDLE_IMPORT_PACKAGE))
                FileUtils.fileAppend(fileName, COMPOSITE_BUNDLE_IMPORT_PACKAGE + ": " + instructions.get(COMPOSITE_BUNDLE_IMPORT_PACKAGE) + "\n");

		} catch (Exception e) {
			throw new MojoExecutionException( "Error writing dependencies into COMPOSITEBUNDLE.MF", e);
		}

	}
    
    /**
     * Calculates bundle symbolic name
     * @param artifact
     * @return
     */
    private String getApplicationSymbolicName(Artifact artifact) {
		if (instructions.containsKey(BUNDLE_SYMBOLIC_NAME))
			return instructions.get(BUNDLE_SYMBOLIC_NAME).toString();
        else
        	return artifact.getGroupId() + "." + artifact.getArtifactId();
    }

    /**
     * Calculates bundle version header
     * @return
     */
    private String getBundleVersion() {
        if (instructions.containsKey(BUNDLE_VERSION))
            return instructions.get(BUNDLE_VERSION).toString();
        else
            return aQute.lib.osgi.Analyzer.cleanupVersion(project.getVersion());
    }

    /**
     * Return build directory
     * @return
     */
    protected File getBuildDir() {
        if ( buildDir == null )
            buildDir = new File( workDirectory );
        return buildDir;
    }

    /**
     * Copy custom manifest file to build dir
     * @throws IOException
     */
    private void includeCustomApplicationManifestFile()  throws IOException
    {
        if (compositeBundleManifestFile == null)
            throw new NullPointerException("CompositeBundle manifest file location not set.  Use <generateManifest>true</generateManifest> if you want it to be generated.");

        File appMfFile = compositeBundleManifestFile;
        if (appMfFile.exists()) {
            getLog().info( "Using COMPOSITEBUNDLE.MF "+ compositeBundleManifestFile);
            File metaInfDir = new File(getBuildDir(), "META-INF");
            FileUtils.copyFileToDirectory( appMfFile, metaInfDir);
        }
    }
    
    /**
     * Only direct dependency artifacts are included ( 'compile' or 'runtime' ).
     */
    private Set<Artifact> selectArtifacts(Set<Artifact> artifacts) {
        Set<Artifact> selected = new LinkedHashSet<Artifact>();
        for (Artifact artifact : artifacts) {
            String scope = artifact.getScope();
            if (scope == null || Artifact.SCOPE_COMPILE.equals(scope) || Artifact.SCOPE_RUNTIME.equals(scope))
                selected.add(artifact);
        }
        return selected;
    }
}
