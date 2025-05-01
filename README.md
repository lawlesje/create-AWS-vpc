# AWS CDK VPC Builder

A robust AWS CDK application for creating customizable Virtual Private Clouds (VPCs) with enhanced networking features and IPv6 support.

## Overview

This project provides an AWS CDK implementation in Java for creating fully-featured VPCs with public and private subnets, NAT Gateways, Internet Gateways, and proper IPv6 support through Egress-Only Internet Gateways. The implementation is designed to be configurable through a JSON file.

## Features

- **Fully Configurable VPC**: Create VPCs with customizable CIDR blocks and DNS settings
- **Dual-Stack Support**: Optional IPv6 support for both public and private subnets
- **Public Subnets**: Automatically configured with direct Internet Gateway access
- **Private Subnets**: Configurable with outbound internet access
- **NAT Gateway**: Provides IPv4 internet access for private subnets
- **Egress-Only Internet Gateway**: Provides IPv6 internet access for private subnets without allowing inbound connections
- **Automatic CIDR Calculation**: Properly spaces subnet CIDR blocks within the VPC CIDR
- **Availability Zone Distribution**: Spreads subnets across AZs for high availability

## Architecture

The VPC architecture includes:

1. **VPC with IPv4 and optional IPv6 CIDR blocks**
2. **Internet Gateway (IGW)** for public subnet internet access
3. **Egress-Only Internet Gateway (EIGW)** for private subnet IPv6 internet access
4. **NAT Gateway** in a public subnet for private subnet IPv4 internet access
5. **Separate route tables** for different types of subnets:
   - Public IPv4 route table
   - Public IPv6 route table
   - Private IPv4 route table
   - Private IPv6 route table
6. **Public and private subnets** across multiple availability zones

## Usage

### Prerequisites

- [AWS CLI](https://aws.amazon.com/cli/) configured with appropriate credentials
- [AWS CDK](https://aws.amazon.com/cdk/) installed
- Java 11 or higher
- Maven

### Configuration

Create a `myVpcConfig.json` file in the project root directory with the following structure:

```json
{
  "vpcName": "MyCustomVPC",
  "cidrBlock": "172.31.0.0/16",
  "enableDnsHostnames": true,
  "enableDnsSupport": true,
  "enableIpv6": true,
  "subnetConfigurations": [
    {
      "name": "PublicSubnet1",
      "subnetType": "PUBLIC",
      "cidrMask": 24,
      "ipv6Enabled": true
    },
    {
      "name": "PublicSubnet2",
      "subnetType": "PUBLIC",
      "cidrMask": 24,
      "ipv6Enabled": false
    },
    {
      "name": "PrivateSubnet1",
      "subnetType": "PRIVATE_WITH_EGRESS",
      "cidrMask": 24,
      "ipv6Enabled": true
    },
    {
      "name": "PrivateSubnet2",
      "subnetType": "PRIVATE_WITH_EGRESS",
      "cidrMask": 24,
      "ipv6Enabled": false
    }
  ]
}
```

### Deployment

1. Build the project:
   ```
   mvn package
   ```

2. Deploy the stack:
   ```
   cdk deploy
   ```

3. To destroy the created resources:
   ```
   cdk destroy
   ```

## Design Decisions

### IPv6-Only Private Subnets

The implementation supports creating IPv6-enabled private subnets that use only the Egress-Only Internet Gateway for IPv6 traffic, without NAT Gateway for IPv4 traffic. This creates true IPv6-only subnets.

### Subnet CIDR Calculation

The `calculateSubnetCidr` method ensures each subnet gets a unique, non-overlapping CIDR range within the VPC's CIDR block:
- For a VPC CIDR of 172.31.0.0/16:
  - Subnet 0: 172.31.0.0/24
  - Subnet 1: 172.31.1.0/24
  - Subnet 2: 172.31.2.0/24
  - etc.

### Availability Zone Distribution

Subnets are distributed across availability zones in a round-robin fashion to ensure high availability.

## Project Structure

- `CreateVpcApp.java`: Main application class that initializes the CDK app
- `CreateVpcStack.java`: Stack class that creates the VPC and all its components
- `myVpcConfig.json`: Configuration file for VPC settings

## Customization

You can customize the VPC by modifying the `myVpcConfig.json` file:

- Change the `cidrBlock` to use a different IP range
- Set `enableIpv6` to `false` to create an IPv4-only VPC
- Add or remove subnets in the `subnetConfigurations` array
- Change subnet types between `PUBLIC` and `PRIVATE_WITH_EGRESS`
- Toggle IPv6 support per subnet with `ipv6Enabled`

## License

This project is licensed under the **BSD 3-Clause License**—a permissive open-source license that allows for modification, distribution, and commercial use while requiring attribution. 

### Key Points:
- You **must** retain the original copyright notice and license text.
- You **can** modify and distribute the code freely.
- You **cannot** use the names of the original authors or contributors for endorsement without permission.

For full details, see the [BSD 3-Clause License](https://fossa.com/blog/open-source-software-licenses-101-bsd-3-clause-license/).

Below is the license badge for quick access:
- [![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)
