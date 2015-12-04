package com.thed.apidocs;

import java.io.File;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by smangal on 5/22/14.
 */

public class ApiaryGeneratorMojoTest {

	private String BASE_PATH = "target/test-classes/apidocsv3/";	// "src/test/resources/apidocs/"
	private String packageName="com.thed.service.rest.resource.v3";
	
    @Before
    public void setup(){
        File file = new File("target/apiary.txt");
        if(file.exists()){
            file.delete();
        }
        File file1 = new File("target/apiary1.txt");
        if(file1.exists()){
        	file1.delete();
        }
    }

    @Test
    public void testExecuteMaven() throws Exception {
        String fileName = "target/apiary.txt";
		File file = new File(fileName);
        System.out.println("apiary.txt file path--"+file.getAbsolutePath());
        Assert.assertFalse(file.exists());
        ApiaryGeneratorMojo mojo = new ApiaryGeneratorMojo();
        mojo.execute();
        Assert.assertTrue(file.exists());
    }

    @Test
    public void testExecuteTwoStep() throws Exception {
    	String fileName = "target/apiary1.txt";
		File file = new File(fileName);
    	System.out.println("apiary1.txt file path--"+file.getAbsolutePath());
    	Assert.assertFalse(file.exists());
    	ApiaryGeneratorMojo mojo = new ApiaryGeneratorMojo(packageName,BASE_PATH,fileName);
    	List<Resource> generateResourceList = mojo.generateResourceList();
    	File generateDocFile = mojo.generateDocFile(generateResourceList);
    	System.out.println(generateDocFile);
    	Assert.assertTrue(file.exists());
    }
   
}
