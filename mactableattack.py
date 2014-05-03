from functools import partial
from mininet.net import Mininet
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.link import TCLink
from mininet.node import CPULimitedHost
from mininet.cli import CLI
from mininet.topo import Topo
from mininet.node import RemoteController
import time

class MactableAttack(Topo):
    
    def __init__(self, **opts):
        # Initialize topology and default options
        Topo.__init__(self, **opts)     
        h1 = self.addHost( 'h1' )                                                                                              
        h2 = self.addHost( 'h2' )                                                                                              
        s1 = self.addSwitch( 's1' )                                                                                         
        self.addLink( h1, s1 )                                                                                                 
        self.addLink( h2, s1 )                                                                                                 



def simpleTest():
    topo=MactableAttack()
    net = Mininet(topo=topo, listenPort=6634, controller = partial(RemoteController, ip='10.0.2.2'))
    net.start()
    h1=net.get('h1')
    h2=net.get('h2')
    #print h1.cmd( 'ping -c1', h2.IP() )
    
    #print "Host", h1.name, "has IP address", h1.IP(), "and MAC address", h1.MAC()
    #print "h1 Mac address:\t" + h1.MAC()                                                            
    mac1 = "fa:dd:fd:b8:bb:aa"
    mac2 = "fa:dd:fd:b8:aa:bb"
    mac3 = "fa:dd:fd:b8:aa:cc"
    #for prefix2 in range(255):
     #   for prefix3 in range(255):
            #print prefix1 + ":" + "%x" % prefix2 + ":" + "%x" % prefix3
    h1.setMAC(mac1)
    h1.cmd("ping -c1", h2.IP())
    h1.setMAC(mac2)
    h1.cmd("ping -c1", h2.IP()) 
    CLI(net)
    #CLI(net)
    h1.setMAC(mac3)
    h1.cmd("ping -c1", h2.IP())
    #h1.setMAC(mac1)
    #CLI(net)
    #h1.cmd("ping -c1", h2.IP())
    #h0.setMAC(mac3)
    #h1.cmd("ping -c1", h2.IP())   
    CLI(net)
    net.stop()  

if __name__ == '__main__':
# Tell mininet to print useful information
    setLogLevel('info')
    simpleTest()
