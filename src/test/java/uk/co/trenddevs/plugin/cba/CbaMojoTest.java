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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.codehaus.plexus.archiver.zip.ZipEntry;
import org.codehaus.plexus.archiver.zip.ZipFile;
import org.codehaus.plexus.util.FileUtils;

public class CbaMojoTest extends AbstractMojoTestCase  {

    public void testCbaTestEnvironment() throws Exception  {
        File testPom = new File( getBasedir(), "target/test-classes/unit/basic-cba-test/plugin-config.xml" );
        CbaMojo mojo = (CbaMojo) lookupMojo( "cba", testPom );
        assertNotNull( mojo );
    }

    public void testBasicCba() throws Exception  {
        testConfiguration("target/test-classes/unit/basic-cba-test/plugin-config.xml" );
    }

    public void testBasicCbaWithDescriptor() throws Exception {
        testConfiguration("target/test-classes/unit/basic-cba-with-descriptor/plugin-config.xml" );
    }

    public void testArchiveContentConfigurationNoBundles() throws Exception  {
        testConfiguration("target/test-classes/unit/basic-cba-no-bundles/plugin-config.xml" );
    }

    public void testArchiveContentConfigurationApplicationContentBundles() throws Exception {
        testConfiguration("target/test-classes/unit/basic-cba-content-bundles-only/plugin-config.xml" );
    }

    public void testCompositeBundleGeneration() throws Exception {

        ZipFile cba = testConfiguration("target/test-classes/unit/basic-cba-without-manifest/plugin-config.xml" );

    	//Test Application-ImportService Application-ExportService and Use-Bundle inclusion
        ZipEntry entry = cba.getEntry("META-INF/COMPOSITEBUNDLE.MF");
        BufferedReader br = new BufferedReader(new InputStreamReader(cba.getInputStream(entry)));

        String appServiceExport = new String("CompositeBundle-ExportService: test.ExportService");
        String appServiceImport = new String("CompositeBundle-ImportService: test.ImportService");
        Boolean foundAppExport=false;
        Boolean foundAppImport=false;

        String line;
        while ((line = br.readLine()) != null) {
        	if (line.contains(new String("CompositeBundle-ExportService"))) {
        		assertEquals(appServiceExport, line);
        		foundAppExport = true;
        	}
        	if (line.contains(new String("CompositeBundle-ImportService"))) {
        		assertEquals(appServiceImport, line);
        		foundAppImport = true;
        	}
            System.out.println(line);
		}
        assertTrue("Found CompositeBundle-ExportService:", foundAppExport);
        assertTrue("Found CompositeBundle-ImportService:", foundAppImport);
    }


    private ZipFile testConfiguration(String pluginConfigFile) throws Exception
    {
        File testPom = new File( getBasedir(), pluginConfigFile );
        CbaMojo mojo = (CbaMojo) lookupMojo( "cba", testPom );
        assertNotNull( mojo );

        String finalName = ( String ) getVariableValueFromObject( mojo, "finalName" );
        String workDir = ( String ) getVariableValueFromObject( mojo, "workDirectory" );
        String outputDir = ( String ) getVariableValueFromObject( mojo, "outputDirectory" );
        mojo.execute();

        //check the generated cba file
        File cbaFile = new File( outputDir, finalName + ".cba" );
        assertTrue( cbaFile.exists() );

        //expected files/directories inside the eba file
        List<String> expectedFiles = Arrays.asList(
                "META-INF/maven/org.apache.maven.test/maven-cba-test/pom.properties",
                "META-INF/maven/org.apache.maven.test/maven-cba-test/pom.xml",
                "META-INF/maven/org.apache.maven.test/maven-cba-test/",
                "META-INF/maven/org.apache.maven.test/",
                "META-INF/maven/",
                "META-INF/COMPOSITEBUNDLE.MF",
                "META-INF/",
                "maven-artifact01-1.0-SNAPSHOT.jar",
                "maven-artifact02-1.0-SNAPSHOT.jar"
        );

        ZipFile cba = new ZipFile( cbaFile );
        Enumeration entries = cba.getEntries();
        assertTrue( entries.hasMoreElements() );

        assertEquals("Missing files: " + expectedFiles,  0, getSizeOfExpectedFiles(entries, expectedFiles));

        return cba;
    }


    private int getSizeOfExpectedFiles( Enumeration entries, List expectedFiles ) {
        expectedFiles = new ArrayList(expectedFiles);
        while( entries.hasMoreElements() ) {
            ZipEntry entry = ( ZipEntry ) entries.nextElement();
            if( !expectedFiles.contains( entry.getName()))
                fail( entry.getName() + " is not included in the expected files" );
            expectedFiles.remove( entry.getName() );
            assertFalse( expectedFiles.contains( entry.getName() ) );
        }
        return expectedFiles.size();
    }

}
