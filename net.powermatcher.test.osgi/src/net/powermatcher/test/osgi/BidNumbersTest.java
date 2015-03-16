package net.powermatcher.test.osgi;

import java.util.List;

import junit.framework.TestCase;
import net.powermatcher.api.monitoring.events.IncomingPriceUpdateEvent;
import net.powermatcher.core.auctioneer.Auctioneer;
import net.powermatcher.core.concentrator.Concentrator;
import net.powermatcher.examples.Freezer;
import net.powermatcher.examples.PVPanelAgent;
import net.powermatcher.examples.StoringObserver;

import org.apache.felix.scr.ScrService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class BidNumbersTest extends TestCase {

	private final String FACTORY_PID_AUCTIONEER = "net.powermatcher.core.auctioneer.Auctioneer";
	private final String FACTORY_PID_CONCENTRATOR = "net.powermatcher.core.concentrator.Concentrator";
	private final String FACTORY_PID_PV_PANEL = "net.powermatcher.examples.PVPanelAgent";
	private final String FACTORY_PID_FREEZER = "net.powermatcher.examples.Freezer";
	private final String FACTORY_PID_OBSERVER = "net.powermatcher.examples.StoringObserver";
	
	private final String AGENT_ID_AUCTIONEER = "auctioneer";
	private final String AGENT_ID_CONCENTRATOR = "concentrator";
	private final String AGENT_ID_PV_PANEL = "pvPanel";
	private final String AGENT_ID_FREEZER = "freezer";
	
	private final BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
    private ServiceReference<?> scrServiceReference = context.getServiceReference( ScrService.class.getName());
    private ScrService scrService = (ScrService) context.getService(scrServiceReference);
    private ConfigurationAdmin configAdmin;
    
    private ClusterHelper cluster;
    
    @Override 
    protected void setUp() throws Exception {
    	super.setUp();
    	
    	configAdmin = getService(ConfigurationAdmin.class);

    	// Cleanup running agents to start with clean test
    	Configuration[] configs = configAdmin.listConfigurations(null);
    	if (configs != null) {
        	for (Configuration config : configs) {
        		config.delete();
        	}
    	}
    }

    /**
     * Tests a simple buildup of a cluster in OSGI and sanity tests.
     * Custer consists of Auctioneer, Concentrator and 2 agents.
     */
    public void testSimpleClusterBuildUp() throws Exception {
    	cluster = new ClusterHelper();
    	// Create Auctioneer
    	Configuration auctioneerConfig = cluster.createConfiguration(configAdmin, FACTORY_PID_AUCTIONEER, cluster.getAuctioneerProperties(AGENT_ID_AUCTIONEER, 1000));

    	// Wait for Auctioneer to become active
    	checkServiceByPid(auctioneerConfig.getPid(), Auctioneer.class);
    	
    	// Create Concentrator
    	Configuration concentratorConfig = cluster.createConfiguration(configAdmin, FACTORY_PID_CONCENTRATOR, cluster.getConcentratorProperties(AGENT_ID_CONCENTRATOR, AGENT_ID_AUCTIONEER, 1000));
    	
    	// Wait for Concentrator to become active
    	checkServiceByPid(concentratorConfig.getPid(), Concentrator.class);
    	
    	// Create PvPanel
    	Configuration pvPanelConfig = cluster.createConfiguration(configAdmin, FACTORY_PID_PV_PANEL, cluster.getPvPanelProperties(AGENT_ID_PV_PANEL, AGENT_ID_CONCENTRATOR, 12));
    	
    	// Wait for PvPanel to become active
    	checkServiceByPid(pvPanelConfig.getPid(), PVPanelAgent.class);

    	// Create Freezer
    	Configuration freezerConfig = cluster.createConfiguration(configAdmin, FACTORY_PID_FREEZER, cluster.getFreezerProperties(AGENT_ID_FREEZER, AGENT_ID_CONCENTRATOR, 1));
    	
    	// Wait for Freezer to become active
    	checkServiceByPid(freezerConfig.getPid(), Freezer.class);
    	
    	// Wait a little time for all components to become satisfied / active
    	Thread.sleep(2000);
    	
    	// check Auctioneer alive
    	assertEquals(true, cluster.checkActive(scrService, FACTORY_PID_AUCTIONEER));
    	// check Concentrator alive
    	assertEquals(true, cluster.checkActive(scrService, FACTORY_PID_CONCENTRATOR));
    	// check PvPanel alive
    	assertEquals(true, cluster.checkActive(scrService, FACTORY_PID_PV_PANEL));
    	// check Freezer alive
    	assertEquals(true, cluster.checkActive(scrService, FACTORY_PID_FREEZER));
    	
    	//Create StoringObserver
    	Configuration storingObserverConfig = cluster.createConfiguration(configAdmin, FACTORY_PID_OBSERVER, cluster.getStoringObserverProperties());
    	
    	// Wait for StoringObserver to become active
    	StoringObserver observer = getServiceByPid(storingObserverConfig.getPid(), StoringObserver.class);
    	
    	//Checking to see if all agents send bids
    	Thread.sleep(10000);
    	checkBidsFullCluster(observer);
    }

    private void checkBidsFullCluster(StoringObserver observer) throws Exception {
    	// Are any bids available for each agent (at all)
    	assertFalse(observer.getOutgoingBidEvents(AGENT_ID_CONCENTRATOR).isEmpty());
    	assertFalse(observer.getOutgoingBidEvents(AGENT_ID_PV_PANEL).isEmpty());
    	assertFalse(observer.getOutgoingBidEvents(AGENT_ID_FREEZER).isEmpty());
    	
    	// Validate bidNumbers of freezer and pvPanel
    	checkBidNumbersWithDifferentPriceUpdates(observer);
    }
    
    private void checkBidNumbersWithDifferentPriceUpdates(StoringObserver observer) throws Exception {
    	List<IncomingPriceUpdateEvent> priceUpdateEventPvPanel = observer.getIncomingPriceUpdateEvents(AGENT_ID_PV_PANEL);
    	List<IncomingPriceUpdateEvent> priceUpdateEventFreezer = observer.getIncomingPriceUpdateEvents(AGENT_ID_FREEZER);
    	boolean sameBidNumberPvPanel = true;
    	
    	// pvpanel will receive same bidNrs because of slow bidUpdateRate
    	// freezer has high bidUpdateRate; pvpanel will receive several prices, but with same bidNr
    	for(int i = 0; i < priceUpdateEventPvPanel.size() - 2; i++) {
    		if (!(priceUpdateEventPvPanel.get(i).getPriceUpdate().getBidNumber() == 
    				priceUpdateEventPvPanel.get(i+1).getPriceUpdate().getBidNumber())) {
    			sameBidNumberPvPanel = false;
    		}
    		assertTrue(sameBidNumberPvPanel);
    	}
    	
    	// freezer will receive different bidNrs because of high bidUpdateRate
    	for(int i = 0; i < priceUpdateEventFreezer.size() -1; i++) {
    		boolean sameBidNumberFreezer = false;
    		if (!(priceUpdateEventFreezer.get(i).getPriceUpdate().getBidNumber() == 
    				priceUpdateEventFreezer.get(i+1).getPriceUpdate().getBidNumber())) {
    			sameBidNumberFreezer = true;
    		}
    		assertTrue(sameBidNumberFreezer);
    	}
    }
    
    private <T> void checkServiceByPid(String pid, Class<T> type) throws InterruptedException {
    	T service = getServiceByPid(pid, type);
        assertNotNull(service);
    }
    
    private <T> T getService(Class<T> type) throws InterruptedException {
        ServiceTracker<T, T> serviceTracker = 
                new ServiceTracker<T, T>(context, type, null);
        serviceTracker.open();
        T result = (T)serviceTracker.waitForService(10000);

        assertNotNull(result);
        
        return result;
    }
    
    private <T> T getServiceByPid(String pid, Class<T> type) throws InterruptedException {
    	String filter = "(" + Constants.SERVICE_PID + "=" + pid + ")";
    	
        ServiceTracker<T, T> serviceTracker;
        T result = null;
		try {
			serviceTracker = new ServiceTracker<T, T>(context, FrameworkUtil.createFilter(filter), null);
		
	        serviceTracker.open();
	        result = type.cast(serviceTracker.waitForService(10000));
		} catch (InvalidSyntaxException e) {
			fail(e.getMessage());
		}

		return result;
    }

}
