package com.wesabe.grendel;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.ssl.SslSocketConnector;

import com.wesabe.grendel.Configuration;

/**
 * Custom Configuration to make grendel run with SSL.
 * 
 * @author Cesar Arevalo
 */
public class GrendelConfiguration extends Configuration {
	
	private static final Logger LOGGER = Logger.getLogger(GrendelConfiguration.class.getCanonicalName());
	public static final String COMMAND_SERVER = "server";
	public static final String COMMAND_SCHEMA = "schema";
	public static final String WRITE_MD5 = "writeMD5";
	private Properties properties = null;
	private CommandLine cmdLine = null;

	public GrendelConfiguration(String[] arguments) throws ParseException, IOException {
		cmdLine = getCmdLine(arguments);
		properties = getProperties(arguments);
	}

	public Connector getConnector() {
		LOGGER.info(GrendelConfiguration.class.getSimpleName() + " - getConnector()");
		
		String sslEnabled = properties.getProperty("ssl.enabled");
		
		if (sslEnabled.equals("true")) {
			SslSocketConnector sslConnector = new SslSocketConnector();
			sslConnector.setPort(Integer.valueOf(properties.getProperty("ssl.port")));
			sslConnector.setMaxIdleTime(Integer.valueOf(properties.getProperty("ssl.maxIdleTime")));
			sslConnector.setKeystore(properties.getProperty("ssl.keystore"));
			sslConnector.setPassword(properties.getProperty("ssl.password"));
			sslConnector.setKeyPassword(properties.getProperty("ssl.keyPassword"));
			sslConnector.setTruststore(properties.getProperty("ssl.truststore"));
			sslConnector.setTrustPassword(properties.getProperty("ssl.trustPassword"));
			return sslConnector;
		}
		else {
			return super.getConnector();
		}
	}

	public Properties getProperties() {
		return this.properties;
	}

	private Properties getProperties(String[] arguments) throws ParseException, IOException {
		final Properties properties = new Properties();
		final FileReader reader = new FileReader(cmdLine.getOptionValue("c"));
		try {
			properties.load(reader);
		} finally {
			reader.close();
		}
		return properties;
	}
	
	public CommandLine getCmdLine(String[] arguments) throws ParseException {
		if (cmdLine != null) {
			return cmdLine;
		}
		
		// Get the properties file
		final String[] subArgs = Arrays.copyOfRange(arguments, 1, arguments.length);
		
		final Options options = new Options();

		final Option configOption = new Option("c", "config", true, null);
		configOption.setRequired(true);
		options.addOption(configOption);

		if (isCommand(arguments, COMMAND_SERVER)) {
			final Option portOption = new Option("p", "port", true, null);
			portOption.setRequired(true);
			options.addOption(portOption);
			
			final Option fileOption = new Option("f", "file", true, null);
			fileOption.setRequired(false);
			options.addOption(fileOption);
		}
		
		options.addOption(null, "migration", false, null);
		
		// Get a handle of utility objects for parsing the command line options
		final GnuParser parser = new GnuParser();
		final CommandLine cmdLine = parser.parse(options, subArgs, false);
		
		return cmdLine;
	}
	
	public static boolean isCommand(String[] arguments, String command) {
		return (arguments.length > 0) && arguments[0].equals(command);
	}
	
	protected void configureRequestLog(RequestLog log) {
		NCSARequestLog requestLog = (NCSARequestLog) log;
		requestLog.setFilename(properties.getProperty("logging.jetty.request.filename"));
		requestLog.setRetainDays(Integer.parseInt(properties.getProperty("logging.jetty.request.retain_days")));
		requestLog.setAppend(Boolean.parseBoolean(properties.getProperty("logging.jetty.request.append")));
		requestLog.setExtended(Boolean.parseBoolean(properties.getProperty("logging.jetty.request.extend")));
		requestLog.setLogTimeZone(properties.getProperty("logging.jetty.request.time_zone"));
	}
	
}
