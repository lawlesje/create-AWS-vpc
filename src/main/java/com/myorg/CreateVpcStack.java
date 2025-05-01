package com.myorg;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import software.amazon.awscdk.CfnTag;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.CfnEIP;
import software.amazon.awscdk.services.ec2.CfnEgressOnlyInternetGateway;
import software.amazon.awscdk.services.ec2.CfnInternetGateway;
import software.amazon.awscdk.services.ec2.CfnNatGateway;
import software.amazon.awscdk.services.ec2.CfnRoute;
import software.amazon.awscdk.services.ec2.CfnRouteTable;
import software.amazon.awscdk.services.ec2.CfnSubnet;
import software.amazon.awscdk.services.ec2.CfnSubnetRouteTableAssociation;
import software.amazon.awscdk.services.ec2.CfnVPC;
import software.amazon.awscdk.services.ec2.CfnVPCCidrBlock;
import software.amazon.awscdk.services.ec2.CfnVPCGatewayAttachment;
import software.constructs.Construct;

/**
 * AWS CDK Stack that creates a Virtual Private Cloud (VPC).
 * This stack creates a fully functional VPC with the following components:
 * - Public subnets with direct internet access via Internet Gateway
 * - Private subnets with outbound internet access via NAT Gateway
 * - IPv6 support for specified subnets
 * - Egress-only Internet Gateway for IPv6 private subnets
 * - Automatic CIDR block calculation for subnets
 * - Route tables for both public and private subnets
 * - NAT Gateway in the first public subnet for private subnet internet access
 * - Separate route tables for IPv6-enabled public and private subnets
 */
public class CreateVpcStack extends Stack {
    /**
     * Default constructor that delegates to the main constructor with null properties.
     * 
     * @param scope Parent construct that this stack will be created within
     * @param id Unique identifier for this stack
     * @throws IOException If there's an error reading the configuration file
     */
    public CreateVpcStack(final Construct scope, final String id) throws IOException {
        this(scope, id, null);
    }

    /**
     * Main constructor that creates the VPC and all its components based on JSON configuration.
     * The VPC creation process follows these steps:
     * 1. Create the VPC with basic settings (CIDR, DNS)
     * 2. Enable IPv6 on the VPC if configured
     * 3. Create and attach Internet Gateway
     * 4. Create Egress-only Internet Gateway for IPv6 if needed
     * 5. Create route tables (public IPv4, public IPv6, private IPv4, private IPv6)
     * 6. Create subnets (both public and private) across AZs with IPv4/IPv6 support as configured
     * 7. Create NAT Gateway in first public subnet
     * 8. Set up appropriate routes for internet access (IPv4 and IPv6)
     * 
     * @param scope Parent construct that this stack will be created within
     * @param id Unique identifier for this stack
     * @param props Additional stack properties (can be null)
     * @throws IOException If there's an error reading the configuration file
     */
    public CreateVpcStack(final Construct scope, final String id, final StackProps props) throws IOException {
        super(scope, id, props);

        // Load VPC configuration from JSON file
        JSONObject config = new JSONObject(new JSONTokener(new FileReader("myVpcConfig.json")));

        // Step 1: Create the VPC with DNS settings
        // DNS support enables domain name resolution
        // DNS hostnames assigns DNS names to instances
        CfnVPC vpc = CfnVPC.Builder.create(this, "VPC")
            .cidrBlock(config.getString("cidrBlock"))
            .enableDnsHostnames(config.getBoolean("enableDnsHostnames"))
            .enableDnsSupport(config.getBoolean("enableDnsSupport"))
            .tags(Arrays.asList(CfnTag.builder()
                .key("Name")
                .value(config.getString("vpcName"))
                .build()))
            .build();

        // Step 2: Enable IPv6 on the VPC if configured
        CfnVPCCidrBlock ipv6CidrBlock = null;
        if (config.getBoolean("enableIpv6")) {
            ipv6CidrBlock = CfnVPCCidrBlock.Builder.create(this, "IPv6CidrBlock")
                .vpcId(vpc.getRef())
                .amazonProvidedIpv6CidrBlock(true)
                .build();
        }

        // Step 3: Create Internet Gateway for public internet access
        // The Internet Gateway enables communication between VPC and internet
        CfnInternetGateway igw = CfnInternetGateway.Builder.create(this, "IGW")
            .tags(Arrays.asList(CfnTag.builder()
                .key("Name")
                .value("igw")
                .build()))
            .build();

        // Attach IGW to VPC - required for internet connectivity
        CfnVPCGatewayAttachment vpcIgwAttachment = CfnVPCGatewayAttachment.Builder.create(this, "IGWAttachment")
            .vpcId(vpc.getRef())
            .internetGatewayId(igw.getRef())
            .build();

        // Step 4: Create Egress-only Internet Gateway for IPv6 if needed
        CfnEgressOnlyInternetGateway eigw = null;
        boolean needsEIGW = false;
        JSONArray subnetConfigs = config.getJSONArray("subnetConfigurations");
        for (int i = 0; i < subnetConfigs.length(); i++) {
            JSONObject subnetConfig = subnetConfigs.getJSONObject(i);
            String subnetType = subnetConfig.getString("subnetType");
            boolean ipv6Enabled = subnetConfig.optBoolean("ipv6Enabled", false);
            
            if (ipv6Enabled && subnetType.equals("PRIVATE_WITH_EGRESS")) {
                needsEIGW = true;
                break;
            }
        }

        if (needsEIGW && config.getBoolean("enableIpv6")) {
            eigw = CfnEgressOnlyInternetGateway.Builder.create(this, "EIGW")
                .vpcId(vpc.getRef())
                .build();
            
            // Unfortunately, EgressOnlyInternetGateway doesn't support tags
            // We'll have to rely on the logical ID "EIGW" for identification
        }

        // Step 5: Create route tables
        // Public IPv4 route table - for subnets that need direct internet access via IPv4
        CfnRouteTable publicIPv4RT = CfnRouteTable.Builder.create(this, "PublicIPv4RT")
            .vpcId(vpc.getRef())
            .tags(Arrays.asList(CfnTag.builder()
                .key("Name")
                .value("public-ipv4-rt")
                .build()))
            .build();

        // Public IPv6 route table - for subnets that need direct internet access via IPv6
        CfnRouteTable publicIPv6RT = CfnRouteTable.Builder.create(this, "PublicIPv6RT")
            .vpcId(vpc.getRef())
            .tags(Arrays.asList(CfnTag.builder()
                .key("Name")
                .value("public-ipv6-rt")
                .build()))
            .build();

        // Private IPv4 route table - for IPv4 subnets that need indirect internet access via NAT
        CfnRouteTable privateIPv4RT = CfnRouteTable.Builder.create(this, "PrivateIPv4RT")
            .vpcId(vpc.getRef())
            .tags(Arrays.asList(CfnTag.builder()
                .key("Name")
                .value("private-ipv4-rt")
                .build()))
            .build();

        // Private IPv6 route table - for IPv6 subnets that need indirect internet access via EIGW
        CfnRouteTable privateIPv6RT = CfnRouteTable.Builder.create(this, "PrivateIPv6RT")
            .vpcId(vpc.getRef())
            .tags(Arrays.asList(CfnTag.builder()
                .key("Name")
                .value("private-ipv6-rt")
                .build()))
            .build();

        // Add IPv4 route to internet via IGW for public subnets
        CfnRoute.Builder.create(this, "PublicRouteIPv4")
            .routeTableId(publicIPv4RT.getRef())
            .destinationCidrBlock("0.0.0.0/0")
            .gatewayId(igw.getRef())
            .build();

        // Copy the IPv4 route to the IPv6-enabled public route table as well
        CfnRoute.Builder.create(this, "PublicIPv6RTRouteIPv4")
            .routeTableId(publicIPv6RT.getRef())
            .destinationCidrBlock("0.0.0.0/0")
            .gatewayId(igw.getRef())
            .build();

        // Add IPv6 route to internet via IGW for public IPv6 subnets
        if (config.getBoolean("enableIpv6")) {
            CfnRoute publicRouteIPv6 = CfnRoute.Builder.create(this, "PublicRouteIPv6")
                .routeTableId(publicIPv6RT.getRef())
                .destinationIpv6CidrBlock("::/0")
                .gatewayId(igw.getRef())
                .build();
            
            // Ensure the IPv6 route is created after the VPC has an IPv6 CIDR block
            publicRouteIPv6.addDependsOn(ipv6CidrBlock);
        }

        // Add IPv6 route via EIGW for private IPv6 subnets
        if (config.getBoolean("enableIpv6") && eigw != null) {
            CfnRoute privateRouteIPv6 = CfnRoute.Builder.create(this, "PrivateRouteIPv6")
                .routeTableId(privateIPv6RT.getRef())
                .destinationIpv6CidrBlock("::/0")
                .egressOnlyInternetGatewayId(eigw.getRef())
                .build();
            
            privateRouteIPv6.addDependsOn(ipv6CidrBlock);
        }

        // Step 6: Create Elastic IP for NAT Gateway
        // NAT Gateway requires a static public IP address
        CfnEIP eip = CfnEIP.Builder.create(this, "NATEIP")
            .domain("vpc")
            .build();

        // Step 7: Create subnets based on configuration
        CfnNatGateway natGateway = null;
        List<CfnSubnet> publicSubnets = new ArrayList<>();
        
        // Create subnets
        for (int i = 0; i < subnetConfigs.length(); i++) {
            JSONObject subnetConfig = subnetConfigs.getJSONObject(i);
            String subnetName = subnetConfig.getString("name");
            String subnetType = subnetConfig.getString("subnetType");
            int cidrMask = subnetConfig.getInt("cidrMask");
            boolean ipv6Enabled = subnetConfig.optBoolean("ipv6Enabled", false);
            
            // Calculate CIDR block for subnet
            String subnetCidr = calculateSubnetCidr(config.getString("cidrBlock"), i, cidrMask);

            // Create subnet builder
            CfnSubnet.Builder subnetBuilder = CfnSubnet.Builder.create(this, subnetName)
                .vpcId(vpc.getRef())
                .cidrBlock(subnetCidr)
                .availabilityZone(getAvailabilityZone(i))
                .mapPublicIpOnLaunch(subnetType.equals("PUBLIC"))
                .tags(Arrays.asList(CfnTag.builder()
                    .key("Name")
                    .value(subnetName)
                    .build()));

            // Build the subnet without IPv6 properties first
            CfnSubnet subnet = subnetBuilder.build();

            // Add IPv6 CIDR block if enabled
            if (ipv6Enabled && config.getBoolean("enableIpv6")) {
                // Add IPv6 properties as direct overrides to the CloudFormation template
                subnet.addPropertyOverride("Ipv6CidrBlock", 
                    Map.of("Fn::Select", Arrays.asList(
                        i,
                        Map.of("Fn::Cidr", Arrays.asList(
                            Map.of("Fn::Select", Arrays.asList(
                                0,
                                Map.of("Fn::GetAtt", Arrays.asList(vpc.getLogicalId(), "Ipv6CidrBlocks"))
                            )),
                            8,
                            "64"
                        ))
                    ))
                );
                
                subnet.addPropertyOverride("AssignIpv6AddressOnCreation", true);
                
                // Ensure subnet waits for IPv6 CIDR block
                if (ipv6CidrBlock != null) {
                    subnet.addDependsOn(ipv6CidrBlock);
                }
            }

            if (subnetType.equals("PUBLIC")) {
                publicSubnets.add(subnet);
                
                // Associate with the appropriate route table based on IPv6 support
                if (ipv6Enabled && config.getBoolean("enableIpv6")) {
                    // Use the public IPv6 route table for IPv6-enabled public subnets
                    CfnSubnetRouteTableAssociation.Builder.create(this, "PublicIPv6RTAssoc" + subnetName)
                        .subnetId(subnet.getRef())
                        .routeTableId(publicIPv6RT.getRef())
                        .build();
                } else {
                    // Use the regular public route table for IPv4-only public subnets
                    CfnSubnetRouteTableAssociation.Builder.create(this, "PublicIPv4RTAssoc" + subnetName)
                        .subnetId(subnet.getRef())
                        .routeTableId(publicIPv4RT.getRef())
                        .build();
                }

                // Create NAT Gateway in the first public subnet
                if (i == 0) {
                    natGateway = CfnNatGateway.Builder.create(this, "NAT")
                        .subnetId(subnet.getRef())
                        .allocationId(eip.getAttrAllocationId())
                        .tags(Arrays.asList(CfnTag.builder()
                            .key("Name")
                            .value("nat")
                            .build()))
                        .build();
                }
            } else if (subnetType.equals("PRIVATE_WITH_EGRESS")) {
                // Associate with the appropriate route table based on IPv6 support
                if (ipv6Enabled && config.getBoolean("enableIpv6")) {
                    // Use the private IPv6 route table for IPv6-enabled private subnets
                    CfnSubnetRouteTableAssociation.Builder.create(this, "PrivateIPv6RTAssoc" + subnetName)
                        .subnetId(subnet.getRef())
                        .routeTableId(privateIPv6RT.getRef())
                        .build();
                } else {
                    // Use the regular private route table for IPv4-only private subnets
                    CfnSubnetRouteTableAssociation.Builder.create(this, "PrivateIPv4RTAssoc" + subnetName)
                        .subnetId(subnet.getRef())
                        .routeTableId(privateIPv4RT.getRef())
                        .build();
                }
            }
        }

        // Add route through NAT Gateway in private IPv4 route table
        if (natGateway != null) {
            CfnRoute.Builder.create(this, "PrivateIPv4RouteToNAT")
                .routeTableId(privateIPv4RT.getRef())
                .destinationCidrBlock("0.0.0.0/0")
                .natGatewayId(natGateway.getRef())
                .build();
        }
    }

    /**
     * Calculates CIDR block for a subnet based on VPC CIDR and subnet index.
     * This method ensures each subnet gets a unique, non-overlapping CIDR range
     * within the VPC's CIDR block. For example:
     * - VPC CIDR: 172.31.0.0/16
     * - Subnet 0: 172.31.0.0/24
     * - Subnet 1: 172.31.1.0/24
     * - Subnet 2: 172.31.2.0/24
     * etc.
     *
     * @param vpcCidr The CIDR block of the VPC (e.g., "172.31.0.0/16")
     * @param subnetIndex The index of the subnet (0-based)
     * @param newMask The subnet mask to use (e.g., 24 for /24)
     * @return Calculated CIDR block for the subnet
     */
    private String calculateSubnetCidr(String vpcCidr, int subnetIndex, int newMask) {
        String[] parts = vpcCidr.split("/");
        String[] octets = parts[0].split("\\.");
        int vpcMask = Integer.parseInt(parts[1]);
        
        // Calculate the new third octet based on the subnet index
        int thirdOctet = Integer.parseInt(octets[2]) + subnetIndex;
        
        return String.format("%s.%s.%d.0/%d", 
            octets[0], octets[1], thirdOctet, newMask);
    }

    /**
     * Gets the Availability Zone identifier for a given subnet index.
     * This method distributes subnets across AZs in a round-robin fashion.
     * For example, with 4 subnets in us-east-1:
     * - Subnet 0 -> us-east-1a
     * - Subnet 1 -> us-east-1b
     * - Subnet 2 -> us-east-1a
     * - Subnet 3 -> us-east-1b
     *
     * @param index The index of the subnet (0-based)
     * @return The availability zone identifier (e.g., "us-east-1a")
     */
    private String getAvailabilityZone(int index) {
        // Assuming us-east-1 region, modify as needed
        return "us-east-1" + (char)('a' + (index % 2));
    }
}