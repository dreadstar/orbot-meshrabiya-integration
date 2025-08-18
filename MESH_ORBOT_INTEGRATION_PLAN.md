# Orbot-Meshrabiya Traffic Routing Integration Plan

## Executive Summary

This document outlines the comprehensive integration plan for routing mesh network traffic through Orbot when nodes are acting as gateway roles (Clearnet Gateway or Tor Proxy Gateway). The integration leverages existing Orbot VPN infrastructure and the mesh networking capabilities to create a seamless traffic routing system.

## Current Architecture Overview

### Orbot VPN Infrastructure
- **OrbotVpnManager**: Core VPN traffic routing through tun2socks to Tor proxy
- **VPN Builder Configuration**: Establishes TUN interface with virtual gateway (192.168.50.1)
- **IPtProxy Integration**: Uses go-tun2socks for packet routing to Tor SOCKS proxy
- **App-based Routing**: Selective app traffic routing through VPN interface
- **DNS Handling**: Custom DNS resolver routing through Tor network

### Mesh Network Architecture
- **VirtualNode/AndroidVirtualNode**: Core mesh networking with MMCP protocol
- **EmergentRoleManager**: Intelligent role assignment including gateway roles
- **MeshRoleManager**: Node fitness calculation and role management
- **GatewayCapabilitiesManager**: User preference management for gateway sharing
- **MeshrabiyaService**: Android service bridging mesh networking and system integration

## Integration Strategy

### Phase 1: Mesh-to-Orbot Bridge Infrastructure

#### 1.1 Create MeshTrafficRouter
A new component that bridges mesh network packets to Orbot's VPN infrastructure:

```kotlin
class MeshTrafficRouter(
    private val orbotService: OrbotService,
    private val virtualNode: VirtualNode,
    private val gatewayCapabilities: GatewayCapabilitiesManager
) {
    private var routingMode: GatewayMode = GatewayMode.NONE
    private var meshVpnInterface: ParcelFileDescriptor? = null
    
    enum class GatewayMode {
        NONE,
        CLEARNET_GATEWAY,  // Route to clearnet via device network
        TOR_GATEWAY        // Route through Orbot VPN to Tor network
    }
    
    fun enableGatewayRouting(mode: GatewayMode) {
        when (mode) {
            GatewayMode.TOR_GATEWAY -> setupTorRouting()
            GatewayMode.CLEARNET_GATEWAY -> setupClearnetRouting()
            GatewayMode.NONE -> disableRouting()
        }
    }
}
```

#### 1.2 Extend OrbotVpnManager for Mesh Integration
Modify OrbotVpnManager to handle mesh-originated traffic:

```java
public class OrbotVpnManager {
    private MeshTrafficHandler meshHandler;
    
    public void enableMeshGateway(boolean enabled) {
        if (enabled) {
            meshHandler = new MeshTrafficHandler(mTorSocks, mTorDns);
            setupMeshRouting();
        } else {
            disableMeshRouting();
        }
    }
    
    private void setupMeshRouting() {
        // Configure TUN interface to accept mesh traffic
        // Add mesh subnet routing rules
        // Setup packet forwarding from mesh to Tor
    }
}
```

### Phase 2: Traffic Flow Implementation

#### 2.1 Clearnet Gateway Traffic Flow
```
Mesh Node A → Virtual Network → Gateway Node → Device Network → Internet
```

**Implementation:**
1. Mesh packets arrive at gateway node via VirtualNode
2. MeshTrafficRouter extracts destination from packet headers
3. For clearnet destinations, route directly through device network interface
4. Implement NAT translation for mesh subnet to device IP
5. Return traffic follows reverse path with address translation

#### 2.2 Tor Gateway Traffic Flow
```
Mesh Node A → Virtual Network → Gateway Node → Orbot VPN → Tor Network → Internet
```

**Implementation:**
1. Mesh packets arrive at gateway node via VirtualNode
2. MeshTrafficRouter identifies Tor-eligible traffic
3. Inject packets into Orbot VPN TUN interface (mInterface)
4. OrbotVpnManager routes through IPtProxy to Tor SOCKS proxy
5. Tor network handles final routing to destination
6. Return traffic follows: Tor → IPtProxy → TUN → MeshTrafficRouter → VirtualNode

### Phase 3: Packet Handling and Routing

#### 3.1 Mesh Packet Interception
Extend AndroidVirtualNode to intercept gateway-bound traffic:

```kotlin
class AndroidVirtualNode {
    private val meshTrafficRouter: MeshTrafficRouter by lazy {
        MeshTrafficRouter(orbotService, this, gatewayCapabilities)
    }
    
    override fun handleIncomingPacket(packet: VirtualPacket) {
        when {
            isGatewayTraffic(packet) -> {
                meshTrafficRouter.routePacket(packet)
            }
            else -> super.handleIncomingPacket(packet)
        }
    }
    
    private fun isGatewayTraffic(packet: VirtualPacket): Boolean {
        // Check if packet is destined for internet (non-mesh address)
        return !packet.destinationAddress.isInMeshSubnet()
    }
}
```

#### 3.2 VPN Interface Integration
Create dedicated mesh routing within OrbotVpnManager:

```java
private void handleMeshPacket(byte[] packetData) {
    try {
        var packet = IpSelector.newPacket(packetData, 0, packetData.length);
        
        if (packet instanceof IpPacket ipPacket) {
            if (isFromMeshSubnet(ipPacket)) {
                // Apply mesh-specific routing rules
                if (shouldRouteThroughTor(ipPacket)) {
                    IPtProxy.inputPacket(packetData);
                } else {
                    routeViaClearnet(ipPacket);
                }
            }
        }
    } catch (IllegalRawDataException e) {
        Log.e(TAG, "Error processing mesh packet", e);
    }
}
```

### Phase 4: Role-Based Traffic Management

#### 4.1 Gateway Role Activation
Integrate with EmergentRoleManager for automatic gateway routing:

```kotlin
class EmergentRoleManager {
    private val meshTrafficRouter: MeshTrafficRouter = createTrafficRouter()
    
    override fun updateCurrentRoles(newRoles: Set<MeshRole>) {
        super.updateCurrentRoles(newRoles)
        
        when {
            MeshRole.TOR_GATEWAY in newRoles -> {
                meshTrafficRouter.enableGatewayRouting(GatewayMode.TOR_GATEWAY)
                announceGatewayCapability(GatewayType.TOR)
            }
            MeshRole.CLEARNET_GATEWAY in newRoles -> {
                meshTrafficRouter.enableGatewayRouting(GatewayMode.CLEARNET_GATEWAY)
                announceGatewayCapability(GatewayType.CLEARNET)
            }
            else -> {
                meshTrafficRouter.enableGatewayRouting(GatewayMode.NONE)
            }
        }
    }
}
```

#### 4.2 Gateway Discovery and Advertisement
Extend MMCP protocol to advertise gateway capabilities:

```kotlin
data class GatewayAnnouncement(
    val nodeId: String,
    val gatewayType: GatewayType,
    val capacity: BandwidthCapacity,
    val latency: NetworkLatency
)

enum class GatewayType {
    CLEARNET,
    TOR,
    I2P
}
```

### Phase 5: Network Configuration

#### 5.1 IP Address Management
- **Mesh Subnet**: `10.255.0.0/16` (for mesh internal traffic)
- **Gateway Virtual IP**: `192.168.50.1` (Orbot VPN gateway)
- **NAT Translation**: Mesh IPs ↔ Device IP for clearnet traffic

#### 5.2 Routing Table Configuration
```
# Mesh-internal traffic
10.255.0.0/16 → Virtual Network Interface

# Internet-bound traffic from mesh
0.0.0.0/0 via Gateway Node → {Tor VPN | Clearnet}

# Return traffic
NAT rules for established connections
```

### Phase 6: Performance and Security

#### 6.1 Traffic Prioritization
- Mesh control traffic (MMCP): High priority
- Gateway traffic: Medium priority  
- Local mesh data: Standard priority

#### 6.2 Security Considerations
- Packet filtering to prevent mesh traffic leakage
- Rate limiting to prevent gateway abuse
- Encryption for all mesh→internet traffic
- Tor circuit isolation for different mesh clients

### Phase 7: User Interface Integration

#### 7.1 Gateway Status Display
Extend MainActivity to show gateway routing status:

```kotlin
data class GatewayStatus(
    val isActive: Boolean,
    val type: GatewayType,
    val connectedClients: Int,
    val throughputStats: ThroughputStats
)
```

#### 7.2 Traffic Monitoring
- Real-time bandwidth usage for mesh clients
- Gateway routing statistics
- Connection status and health metrics

## Implementation Roadmap

### Week 1: Core Infrastructure
- [ ] Create MeshTrafficRouter component
- [ ] Extend OrbotVpnManager for mesh integration
- [ ] Implement basic packet routing infrastructure

### Week 2: Traffic Flow Implementation  
- [ ] Implement clearnet gateway routing
- [ ] Implement Tor gateway routing through VPN
- [ ] Add packet filtering and NAT translation

### Week 3: Role Integration
- [ ] Integrate with EmergentRoleManager
- [ ] Implement gateway capability advertisement
- [ ] Add automatic role-based routing activation

### Week 4: Testing and Optimization
- [ ] Performance testing with multiple mesh clients
- [ ] Security testing and packet isolation
- [ ] UI integration and status monitoring

## Technical Challenges and Solutions

### Challenge 1: Packet Loop Prevention
**Problem**: Mesh packets entering VPN could loop back to mesh
**Solution**: Source address tagging and routing table rules to prevent loops

### Challenge 2: NAT State Management
**Problem**: Maintaining NAT state for multiple mesh clients
**Solution**: Connection tracking table with mesh node ID mapping

### Challenge 3: DNS Resolution
**Problem**: Mesh clients need DNS resolution
**Solution**: Extend Orbot's DNS resolver to handle mesh client requests

### Challenge 4: Performance Impact
**Problem**: Additional packet processing overhead
**Solution**: Async packet processing and connection pooling

## Success Metrics

1. **Connectivity**: Mesh nodes can access internet through gateways
2. **Performance**: <50ms additional latency for gateway routing
3. **Throughput**: >80% of available bandwidth utilization
4. **Reliability**: 99%+ uptime for gateway routing
5. **Security**: Zero traffic leakage outside intended paths

## Future Enhancements

1. **Multi-gateway Load Balancing**: Distribute traffic across multiple gateways
2. **Dynamic Gateway Selection**: Choose optimal gateway based on latency/bandwidth
3. **Exit Node Selection**: Allow mesh clients to specify preferred Tor exit countries
4. **Traffic Analysis**: Detailed analytics for network optimization

This integration plan provides a comprehensive approach to routing mesh network traffic through Orbot, leveraging existing infrastructure while maintaining security and performance standards.
