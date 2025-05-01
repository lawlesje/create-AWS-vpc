package com.myorg;

import java.io.IOException;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * Main application class for VPC creation.
 * This class initializes the CDK app and creates the VPC stack that mimics
 * the AWS default VPC structure with dual-stack support.
 */
public class CreateVpcApp {
    /**
     * Main entry point for the application.
     * Initializes the CDK app and creates the VPC stack with configuration.
     * 
     * @param args Command line arguments, with the first argument optionally 
     *             specifying the path to the configuration JSON file
     */
    public static void main(final String[] args) throws IOException {
        App app = new App();
        
        // Get configuration file path from command line arguments if provided
        String configFilePath = null;
        if (args.length > 0) {
            configFilePath = args[0];
        }
        
        // Set up the environment with AWS account and region
        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();
        
        // Create the VPC stack with the specified configuration
        try {
            CreateVpcStack vpcStack = new CreateVpcStack(app, "MyVpcStack", StackProps.builder()
                    .env(env)
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1); // Exit the program if stack creation fails
        }
        
        // Synthesize the CloudFormation template
        app.synth();
    }
}