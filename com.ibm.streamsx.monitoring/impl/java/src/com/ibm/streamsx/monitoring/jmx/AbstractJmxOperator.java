//
// ****************************************************************************
// * Copyright (C) 2016, International Business Machines Corporation          *
// * All rights reserved.                                                     *
// ****************************************************************************
//

package com.ibm.streamsx.monitoring.jmx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.log4j.Logger;


import com.ibm.json.java.JSON;
import com.ibm.json.java.JSONArtifact;
import com.ibm.json.java.JSONObject;
import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.ProcessingElement;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Parameter;

/**
 * Abstract class for the JMX operators.
 */
public abstract class AbstractJmxOperator extends AbstractOperator {

	// ------------------------------------------------------------------------
	// Documentation.
	// Attention: To add a newline, use \\n instead of \n.
	// ------------------------------------------------------------------------
	
	protected static final String DESC_PARAM_APPLICATION_CONFIGURATION_NAME = 
			"Specifies the name of [https://www.ibm.com/support/knowledgecenter/en/SSCRJU_4.2.0/com.ibm.streams.admin.doc/doc/creating-secure-app-configs.html|application configuration object] "
			+ "that can contain instanceId, connectionURL, user, password and filterDocument "
			+ "properties. The application configuration overrides values that "
			+ "are specified with the corresponding parameters.";
	
	protected static final String DESC_PARAM_CONNECTION_URL = 
			"Specifies the connection URL as returned by the `streamtool "
			+ "getjmxconnect` command. If the **applicationConfigurationName** "
			+ "parameter is specified, the application configuration can "
			+ "override this parameter value.";
	
	protected static final String DESC_PARAM_USER = 
			"Specifies the user that is required for the JMX connection. If "
			+ "the **applicationConfigurationName** parameter is specified, "
			+ "the application configuration can override this parameter value.";
	
	protected static final String DESC_PARAM_PASSWORD = 
			"Specifies the password that is required for the JMX connection. If "
			+ "the **applicationConfigurationName** parameter is specified, "
			+ "the application configuration can override this parameter value.";

	protected static final String DESC_PARAM_SSL_OPTION = 
			"Specifies the sslOption that is required for the JMX connection. If "
			+ "the **applicationConfigurationName** parameter is specified, "
			+ "the application configuration can override this parameter value. Default is TLSv1.2";
	
	protected static final String DESC_PARAM_INSTANCE_ID = 
			"Specifies the instance id used to select the instance that is monitored. If no value is "
			+ "specified, the instance id under which this operator is running is monitored."
			+ " If the **applicationConfigurationName** parameter is specified, "
			+ "the application configuration can override this parameter value.";	
	
	protected static final Object PARAMETER_CONNECTION_URL = "connectionURL";
	
	protected static final Object PARAMETER_USER = "user";

	protected static final Object PARAMETER_PASSWORD = "password";
	
	protected static final Object PARAMETER_SSL_OPTION = "sslOption";

	protected static final Object PARAMETER_FILTER_DOCUMENT = "filterDocument";
	
	protected static final Object PARAMETER_INSTANCE_ID = "instanceId";

	protected static final String MISSING_VALUE = "The following value must be specified as parameter or in the application configuration: ";

	// ------------------------------------------------------------------------
	// Implementation.
	// ------------------------------------------------------------------------
	
	/**
	 * Logger for tracing.
	 */
	private static Logger _trace = Logger.getLogger(AbstractJmxOperator.class.getName());
	
	protected OperatorConfiguration _operatorConfiguration = new OperatorConfiguration();
		
	/**
	 * The base directory of the application
	 */	
	protected File baseDir = null;
	
	private Metric isConnected;
	private Metric nJMXConnectionAttempts;
	private Metric nBrokenJMXConnections;

    public Metric get_nJMXConnectionAttempts() {
        return this.nJMXConnectionAttempts;
    }
	
    public Metric get_isConnected() {
        return this.isConnected;
    }

    public Metric get_nBrokenJMXConnections() {
        return this.nBrokenJMXConnections;
    }
    
    @CustomMetric(name="nBrokenJMXConnections", kind = Kind.COUNTER, description = "Number of broken JMX connections that have occurred. Notifications may have been lost.")
    public void set_nConnectionLosts(Metric nBrokenJMXConnections) {
        this.nBrokenJMXConnections = nBrokenJMXConnections;
    }
    
    @CustomMetric(name="nJMXConnectionAttempts", kind = Kind.COUNTER, description = "Number of connection attempts to JMX service.")
    public void set_nJMXConnectionAttempts(Metric nConnectionAttempts) {
        this.nJMXConnectionAttempts = nConnectionAttempts;
    }
    
    @CustomMetric(name="isConnected", kind = Kind.GAUGE, description = "Value 1 indicates, that this operator is connected to JMX service. Otherwise value 0 is set, if no connection is established.")
    public void set_isConnected(Metric isConnected) {
        this.isConnected = isConnected;
    }

	@Parameter(
			optional=true,
			description=AbstractJmxOperator.DESC_PARAM_CONNECTION_URL
			)
	public void setConnectionURL(String connectionURL) {
		_operatorConfiguration.set_connectionURL(connectionURL);
	}

	@Parameter(
			optional=true,
			description=AbstractJmxOperator.DESC_PARAM_USER
			)
	public void setUser(String user) {
		_operatorConfiguration.set_user(user);
	}

	@Parameter(
			optional=true,
			description=AbstractJmxOperator.DESC_PARAM_PASSWORD
			)
	public void setPassword(String password) {
		_operatorConfiguration.set_password(password);
	}

	@Parameter(
			optional=true,
			description=AbstractJmxOperator.DESC_PARAM_SSL_OPTION
			)
	public void setSslOption(String sslOption) {
		_operatorConfiguration.set_sslOption(sslOption);
	}

	@Parameter(
			optional=true,
			description=AbstractJmxOperator.DESC_PARAM_INSTANCE_ID
			)
	public void setInstanceId(String instanceId) {
		_operatorConfiguration.set_instanceId(instanceId);
	}	
	
	@Parameter(
			optional=true,
			description=AbstractJmxOperator.DESC_PARAM_APPLICATION_CONFIGURATION_NAME
			)
	public void setApplicationConfigurationName(String applicationConfigurationName) {
		_operatorConfiguration.set_applicationConfigurationName(applicationConfigurationName);
	}
	
	/**
	 * Initialize this operator. Called once before any tuples are processed.
	 * @param context OperatorContext for this operator.
	 * @throws Exception Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
		// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
		
		// Dynamic registration of additional class libraries to get rid of
		// the @Libraries annotation and its compile-time evaluation of
		// environment variables.
		setupClassPaths(context);

		// check required parameters
		if (null == _operatorConfiguration.get_applicationConfigurationName()) {
			if ((null == _operatorConfiguration.get_user()) && (null == _operatorConfiguration.get_password()) ) {
				throw new com.ibm.streams.operator.DataException("The " + context.getName() + " operator requires parameters 'user' and 'password' or 'applicationConfigurationName' be applied.");
			}
		}
		else {
			if (context.getPE().isStandalone()) {
				if ((null == _operatorConfiguration.get_user()) && (null == _operatorConfiguration.get_password())) {
					throw new com.ibm.streams.operator.DataException("The " + context.getName() + " operator requires parameters 'user' and 'password' and 'instanceId' applied, when running in standalone mode. Application configuration is supported in distributed mode only.");
				}				
			}
		}
		
		this.baseDir = context.getPE().getApplicationDirectory();

		/*
		 * The instanceId parameter is optional. If the application developer does
		 * not set it, use the instance id under which the operator itself is
		 * running.
		 */
		if (_operatorConfiguration.get_instanceId() == null) {
			if (context.getPE().isStandalone()) {
				throw new com.ibm.streams.operator.DataException("The " + context.getName() + " operator runs in standalone mode and can, therefore, not automatically determine a instance id. The following value must be specified as parameter: instanceId");
			}
			String instanceId = getApplicationConfigurationInstanceId();
			if ("".equals(instanceId)) {
				_operatorConfiguration.set_instanceId(context.getPE().getInstanceId());
				_trace.info("The " + context.getName() + " operator automatically connects to the " + _operatorConfiguration.get_instanceId() + " instance.");
			}
			else {
				_operatorConfiguration.set_instanceId(instanceId);
				_trace.info("The " + context.getName() + " operator connects to the " + _operatorConfiguration.get_instanceId() + " instance specified by application configuration.");
			}
		}
		_operatorConfiguration.set_defaultFilterInstance(_operatorConfiguration.get_instanceId());
		
	}	
	
	/**
	 * Registers additional class libraries to get rid of the @Libraries
	 * annotation for the operator. Although the @Libraries annotation
	 * supports environment variables, these are evaluated during
	 * compile-time only requiring identical IBM Streams installation paths
	 * for the build and run-time environment.
	 *  
	 * @param context
	 * Context information for the Operator's execution context.
	 * 
	 * @throws Exception 
	 * Throws exception in case of unavailable STREAM_INSTALL environment
	 * variable, or problems while loading the required class libraries.
	 */
	protected void setupClassPaths(OperatorContext context) throws Exception {
		final String STREAMS_INSTALL = System.getenv("STREAMS_INSTALL");
		if (STREAMS_INSTALL == null || STREAMS_INSTALL.isEmpty()) {
			throw new Exception("STREAMS_INSTALL environment variable must be set");
		}
		// See: http://www.ibm.com/support/knowledgecenter/en/SSCRJU_4.2.0/com.ibm.streams.dev.doc/doc/jmxapi-start.html
		String[] libraries = {
				STREAMS_INSTALL + "/lib/com.ibm.streams.management.jmxmp.jar",
				STREAMS_INSTALL + "/lib/com.ibm.streams.management.mx.jar",
				STREAMS_INSTALL + "/ext/lib/jmxremote_optional.jar",
				STREAMS_INSTALL + "/ext/lib/JSON4J.jar"
		};
		try {
			context.addClassLibraries(libraries);
		}
		catch(MalformedURLException e) {
			_trace.error("problem while adding class libraries: " + String.join(",", libraries), e);
			throw e;
		}
	}
	

	protected String getApplicationConfigurationInstanceId() {
		String result = "";
		String applicationConfigurationName = _operatorConfiguration.get_applicationConfigurationName();
		if (applicationConfigurationName != null) {
			Map<String,String> properties = getApplicationConfiguration(applicationConfigurationName);
			if (properties.containsKey(PARAMETER_INSTANCE_ID)) {
				result = properties.get(PARAMETER_INSTANCE_ID);
			}
		}
		return result;
	}	

	/**
	 * Sets up a JMX connection. The connection URL, the user, and the password
	 * can be set with the operator parameters, or via application configuration.
	 * 
	 * @throws Exception
	 * Throws exception in case of invalid/bad URLs or other I/O problems.
	 */
	protected void setupJMXConnection() throws Exception {
		// Apply defaults, which are the parameter values.
		String connectionURL = _operatorConfiguration.get_connectionURL();
		String user = _operatorConfiguration.get_user();
		String password = _operatorConfiguration.get_password();
		String sslOption = _operatorConfiguration.get_sslOption();
		// Override defaults if the application configuration is specified
		String applicationConfigurationName = _operatorConfiguration.get_applicationConfigurationName();
		if (applicationConfigurationName != null) {
			Map<String,String> properties = getApplicationConfiguration(applicationConfigurationName);
			if (properties.containsKey(PARAMETER_CONNECTION_URL)) {
				connectionURL = properties.get(PARAMETER_CONNECTION_URL);
			}
			if (properties.containsKey(PARAMETER_USER)) {
				user = properties.get(PARAMETER_USER);
			}
			if (properties.containsKey(PARAMETER_PASSWORD)) {
				password = properties.get(PARAMETER_PASSWORD);
			}
			if (properties.containsKey(PARAMETER_SSL_OPTION)) {
				sslOption = properties.get(PARAMETER_SSL_OPTION);
			}
		}
		// Ensure a valid configuration.
		if (connectionURL == null) {
			// not configured via parameter or application configuration
			connectionURL = autoDetectJmxConnect();
			if (connectionURL == null) {
				throw new Exception(MISSING_VALUE + PARAMETER_CONNECTION_URL);
			}
		}
		if (user == null) {
			throw new Exception(MISSING_VALUE + PARAMETER_USER);
		}
		if (password == null) {
			throw new Exception(MISSING_VALUE + PARAMETER_PASSWORD);
		}

		/*
		 * Prepare the JMX environment settings.
		 */
		HashMap<String, Object> env = new HashMap<>();
		String [] credentials = { user, password };
		env.put("jmx.remote.credentials", credentials);
		env.put("jmx.remote.protocol.provider.pkgs", "com.ibm.streams.management");

		if (sslOption != null) {
			env.put("jmx.remote.tls.enabled.protocols", sslOption);
		}

		/*
		 * Setup the JMX connector and MBean connection.
		 */
		String[] urls = connectionURL.split(","); // comma separated list of JMX servers is supported
		// In Streaming Analytics service the variable urls contains 3 JMX servers. The third JMX is the prefered one. 
		for (int i=urls.length-1; i>=0; i--) {
			try {
				get_nJMXConnectionAttempts().increment(); // update metric
				_trace.info("Connect to : " + urls[i]);
				_operatorConfiguration.set_jmxConnector(JMXConnectorFactory.connect(new JMXServiceURL(urls[i]), env));
				get_isConnected().setValue(1);
				break; // exit loop here since a valid connection is established, otherwise exception is thrown.
			} catch (IOException e) {
				_trace.error("connect failed: " + e.getMessage());
				if (i == 0) {
					get_isConnected().setValue(0);
					throw e;
				}
			}
		}
		_operatorConfiguration.set_mbeanServerConnection(_operatorConfiguration.get_jmxConnector().getMBeanServerConnection());
	}
	

	/**
	 * Calls the ProcessingElement.getApplicationConfiguration() method to
	 * retrieve the application configuration if application configuration
	 * is supported.
	 * 
	 * @return
	 * The application configuration.
	 */
	@SuppressWarnings("unchecked")
	protected Map<String,String> getApplicationConfiguration(String applicationConfigurationName) {
		Map<String,String> properties = null;
		try {
			ProcessingElement pe = getOperatorContext().getPE();
			Method method = ProcessingElement.class.getMethod("getApplicationConfiguration", new Class[]{String.class});
			Object returnedObject = method.invoke(pe, applicationConfigurationName);
			properties = (Map<String,String>)returnedObject;
		}
		catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			properties = new HashMap<>();
		}
		return properties;
	}
	
	private String autoDetectJmxConnect() throws Exception {
		// STREAMS_JMX_CONNECT
		final String STREAMS_JMX_CONNECT = System.getenv("STREAMS_JMX_CONNECT");
		if (STREAMS_JMX_CONNECT == null || STREAMS_JMX_CONNECT.isEmpty()) {
			_trace.error("STREAMS_JMX_CONNECT environment variable is not set.");
		}
		return STREAMS_JMX_CONNECT;
	}	
	
}
