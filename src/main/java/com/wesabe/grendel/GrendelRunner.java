package com.wesabe.grendel;

import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.wesabe.grendel.util.CipherUtil;
import com.codahale.shore.HelpCommand;
import com.codahale.shore.Shore;
import com.wesabe.grendel.Configuration;
import com.wesabe.grendel.openpgp.KeySetGenerator.KeyPairHolder;

/**
 * The class from which we run Grendel.
 * 
 * @author Cesar Arevalo
 */
public class GrendelRunner {

	private final static String MAIN_USAGE_TEMPLATE =
		"usage: {app} <subcommand> [options]\n" +
		"\n" +
		"Available subcommands:\n" +
		"   server    Run {app} as an HTTP server.\n" +
		"   schema    Generate a database schema for {app}.\n" +
		"   writeMD5  Generate the md5 passphrase file for {app}.\n";
	private static final String PASSPHRASE_DO_NOT_MATCH = "The passphrase's entered do not match. Please try again.";

	public static void main(String[] args) throws Exception {
		
		/**
		 * If there is no command then show help.s
		 */
		if (!(GrendelConfiguration.isCommand(args, GrendelConfiguration.COMMAND_SCHEMA) ||
				GrendelConfiguration.isCommand(args, GrendelConfiguration.COMMAND_SERVER) ||
				GrendelConfiguration.isCommand(args, GrendelConfiguration.WRITE_MD5))) {
			
			new HelpCommand(usage(new Configuration(), MAIN_USAGE_TEMPLATE, null), System.out).run();
			return;
		}
		
		/**
		 * We have at least one valid command for running.
		 */
		GrendelConfiguration grendelConfiguration = new GrendelConfiguration(args);
		CommandLine cmdLine = grendelConfiguration.getCmdLine(args);
		
		/**
		 * Command for creating the schema
		 */
		if (GrendelConfiguration.isCommand(args, "schema")) {
			List<String> shoreArgs = new ArrayList<String>();
			shoreArgs.add(GrendelConfiguration.COMMAND_SCHEMA);
			shoreArgs.add("-c");
			shoreArgs.add(cmdLine.getOptionValue("c"));
			if (cmdLine.hasOption("migration")) {
				shoreArgs.add("--migration");
			}
			Shore.run(new Configuration(), shoreArgs.toArray(new String[shoreArgs.size()]));
			return;
		}

		/**
		 * Are we running grendel as a service? If we are then we will receive
		 * two passphrase arguments. Check these arguments and proceed if
		 * successful.
		 */
		if (isRunningAsService(cmdLine)) {
			String filename = cmdLine.getOptionValue("file");

			String contents = CipherUtil.getContents(filename);
			char[] passphrase1 = contents.split(":")[0].toCharArray();
			char[] passphrase2 = contents.split(":")[1].toCharArray();

			if (compare(passphrase1, passphrase2)) {
				setPassphrase(passphrase1);
			} else {
				throw new UnsupportedOperationException(PASSPHRASE_DO_NOT_MATCH);
			}
		}
		/**
		 * We are not running grendel as a service. It is being run from the
		 * command line.
		 */
		else {
			confirmPassphrase(GrendelRunner.getPassphrase());
		}

		String pathname = grendelConfiguration.getProperties().getProperty(
				"passphrase.file");

		if (GrendelConfiguration
				.isCommand(args, GrendelConfiguration.WRITE_MD5)) {
			if (pathname != null && !pathname.isEmpty()) {
				CipherUtil.writeToFileAsMD5Checksum(GrendelRunner
						.getPassphrase(), pathname);
			} else {
				throw new UnsupportedOperationException(
						"Please specify a passphrase.file in the properties file.");
			}
		} else if (CipherUtil.compareChecksum(GrendelRunner.getPassphrase(),
				pathname)) {
			KeyPair masterKeyPair = KeyPairHolder.MASTER_KEY_PAIR;
			KeyPair subKeyPair = KeyPairHolder.SUB_KEY_PAIR;
			if (masterKeyPair == null ||
					subKeyPair == null) {
				throw new UnsupportedOperationException("We need to have the key pairs by now.");
			}
			String[] shoreArgs = new String[]{GrendelConfiguration.COMMAND_SERVER, "-c", cmdLine.getOptionValue("c"), "-p", cmdLine.getOptionValue("p")};
			Shore.run(grendelConfiguration, shoreArgs);
		} else {
			throw new UnsupportedOperationException(
					"The passphrase is incorrect.");
		}
	}

	private static void confirmPassphrase(char[] passphrase) {
		char[] passphrase2 = PassphraseHolder
				.readPassword("Confirm passphrase: ");
		compare(passphrase, passphrase2);
	}

	private static boolean compare(char[] passphrase1, char[] passphrase2) {
		boolean areEqual = true;
		if (passphrase1.length != passphrase2.length) {
			areEqual = false;
		}
		for (int index = 0; index < passphrase1.length; index++) {
			if (passphrase1[index] != passphrase2[index]) {
				areEqual = false;
				break;
			}
		}
		if (!areEqual) {
			throw new UnsupportedOperationException(PASSPHRASE_DO_NOT_MATCH);
		}
		return areEqual;
	}

	public static char[] getPassphrase() {
		if (PassphraseHolder.PASSPHRASE == null) {
			PassphraseHolder.PASSPHRASE = PassphraseHolder
					.readPassword("Enter passphrase: ");
		}
		return PassphraseHolder.PASSPHRASE;
	}

	private static boolean isRunningAsService(CommandLine cmdLine) {
		if (cmdLine.hasOption("file")) {
			return true;
		} else {
			return false;
		}
	}

	private static void setPassphrase(char[] passphrase) {
		PassphraseHolder.PASSPHRASE = passphrase;
	}

	/**
	 * Design for storing the passphrase.
	 * 
	 * @author Cesar Arevalo
	 * @see <a
	 *      href="http://en.wikipedia.org/wiki/Initialization_on_demand_holder_idiom">Initialization
	 *      on demand holder idiom</a>
	 */
	private static class PassphraseHolder {
		private static char[] PASSPHRASE;

		/**
		 * Read a password from the Console.
		 * 
		 * @see <a
		 *      href="http://java.sun.com/javase/6/docs/technotes/guides/security/crypto/CryptoSpec.html#ReadPassword">here</a>
		 *      .
		 * @return A character array containing the password or passphrase, not
		 *         including the line-termination characters, or null if an end
		 *         of stream has been reached.
		 * 
		 * @throws IOException
		 *             if an I/O problem occurs
		 */
		private static final char[] readPassword(String message) {

			/*
			 * If available, directly use the java.io.Console class to avoid
			 * character echoing.
			 */
			if (System.console() != null) {
				// readPassword returns "" if you just print ENTER,
				return System.console().readPassword(message);
			} else {
				// TODO: The return value is here so we can continue to run the
				// tests.
				// Alternatives:
				// - Set PASSPHRASE public. Modify in tests
				// - Do no testing. Keep PASSPHRASE private.
				// - Do nothing and keep this code.
				return ("The long and winding road" + "That leads to your door"
						+ "Will never disappear" + "I've seen that door before"
						+ "It always leads me here" + "Leads me to your door")
						.toCharArray();
			}

		}
	}
	
	private static String usage(Configuration configuration, String template, String error) {
		final StringBuilder builder = new StringBuilder();
		if (error != null) {
			builder.append("Error: ").append(error).append("\n\n");
		}
		return builder.append(template.replace("{app}", configuration.getExecutableName())).toString();
	}

}
