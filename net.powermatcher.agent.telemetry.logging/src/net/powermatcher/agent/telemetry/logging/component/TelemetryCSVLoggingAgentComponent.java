package net.powermatcher.agent.telemetry.logging.component;

import java.util.Map;

import net.powermatcher.agent.telemetry.logging.TelemetryCSVLoggingAgent;
import net.powermatcher.core.adapter.component.ConnectableRegistration;
import net.powermatcher.core.configurable.BaseConfiguration;
import net.powermatcher.core.configurable.service.ConfigurationService;
import net.powermatcher.core.object.ConnectableObject;

import org.osgi.framework.BundleContext;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;

/**
 * @author IBM
 * @version 0.9.0
 */
@Component(name = TelemetryCSVLoggingAgentComponent.COMPONENT_NAME, designateFactory = TelemetryCSVLoggingAgentComponentConfiguration.class)
public class TelemetryCSVLoggingAgentComponent extends TelemetryCSVLoggingAgent {
	/**
	 * Define the component name (String) constant.
	 */
	public final static String COMPONENT_NAME = "net.powermatcher.agent.telemetry.logging.TelemetryCSVLoggingAgent";
	/**
	 * Define the service registration (ConnectableRegistration) constant.
	 */
	private ConnectableRegistration<ConnectableObject> serviceRegistration  = new ConnectableRegistration<ConnectableObject>(this);

	/**
	 * Activate with the specified properties parameter.
	 * 
	 * @param context OSGi bundle context.
	 * @param properties
	 *            The properties (<code>Map<String,Object></code>) parameter.
	 */
	@Activate
	void activate(final BundleContext context, final Map<String, Object> properties) {
		ConfigurationService configuration = new BaseConfiguration(properties);
		setConfiguration(configuration);
		this.serviceRegistration.register(context);
	}

	/**
	 * Deactivate.
	 */
	@Deactivate
	void deactivate() {
		this.serviceRegistration.unregister();
	}

}