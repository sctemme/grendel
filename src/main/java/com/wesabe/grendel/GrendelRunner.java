package com.wesabe.grendel;

import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.hibernate.Session;
import org.hibernate.cfg.AnnotationConfiguration;
import com.codahale.shore.HelpCommand;
import com.codahale.shore.Shore;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wesabe.grendel.entities.MD5Checksum;
import com.wesabe.grendel.entities.Passphrase;
import com.wesabe.grendel.entities.dao.MD5DAO;
import com.wesabe.grendel.entities.dao.PassphraseDAO;
import com.wesabe.grendel.openpgp.KeySetGenerator.KeyPairHolder;
import com.wesabe.grendel.util.CipherUtil;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;

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
		final GrendelConfiguration grendelConfiguration = new GrendelConfiguration(args);
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
		
		Injector injector =
		    Guice.createInjector(PersistenceService.usingHibernate()
		            .across(UnitOfWork.TRANSACTION)
		            .buildModule(),
		            new AbstractModule() {
		        protected void configure() {
		            bind(org.hibernate.cfg.Configuration.class)
		            .toInstance(new AnnotationConfiguration()
		            .addAnnotatedClass(Passphrase.class)
		            .addAnnotatedClass(MD5Checksum.class)
		            .setProperties(grendelConfiguration.getProperties()));
		        }
		    });
	            
		injector.getInstance(PersistenceService.class).start();
		PassphraseDAO passphraseDao = injector.getInstance(PassphraseDAO.class);

		/**
		 * Are we running grendel as a service? If we are then we will receive
		 * two passphrase arguments. Check these arguments and proceed if
		 * successful.
		 */
		/*
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
		*/
		/**
		 * We are not running grendel as a service. It is being run from the
		 * command line.
		 */
		Session session = injector.getInstance(Session.class);
		session.beginTransaction();
		List<Passphrase>  passphrases = passphraseDao.findEnabledPassphrases();
		session.getTransaction().commit();

		for(Passphrase pp : passphrases) {
		    confirmPassphrase(GrendelRunner.getPassphrase(pp));
		    String activate = GrendelRunner.readActivate("activate this passphrase?, (Y/N) only one passphrase can be active:\n");
		    pp.setActive(activate.equals("Y")? true : false);
		}
		savePassphrase(passphrases, passphraseDao, injector.getInstance(Session.class));

		MD5DAO md5Dao = injector.getInstance(MD5DAO.class);
		session = injector.getInstance(Session.class);
		session.beginTransaction();
		MD5Checksum md5Checksum = md5Dao.getById(0);
		session.getTransaction().commit();

		if (GrendelConfiguration.isCommand(args, GrendelConfiguration.WRITE_MD5)) {
		    if(md5Checksum == null) {
		        md5Checksum = new MD5Checksum();
		        md5Checksum.setId(0);
		    }
		    md5Checksum.setMD5(CipherUtil.getMD5Checksum(PassphraseHolder.getPassphrase()).getBytes());
		    session = injector.getInstance(Session.class);
		    session.beginTransaction();
		    md5Dao.saveOrUpdate(md5Checksum);
		    session.getTransaction().commit();
				
		} else if (CipherUtil.compareChecksum(PassphraseHolder.getPassphrase(), md5Checksum.getMD5())) {
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
					"Passphrase md5 checksum unmatched.");
		}
	}

	private static void savePassphrase(List<Passphrase> passphrases, PassphraseDAO passphraseDao, Session session)
    {
        int n = 0;
        for(Passphrase pp : passphrases) {
            if(pp.isActive()) {
                n = n + 1;
            }
        }
        if(n != 1) {
            throw new RuntimeException("There must be exactly one passphrase that should be active");
        }
        session.beginTransaction();
        for(Passphrase pp : passphrases) {
            passphraseDao.saveOrUpdate(pp);
        }
        session.getTransaction().commit();
        
    }
	
    private static void confirmPassphrase(char[] passphrase) {
		char[] read = PassphraseHolder
				.readPassword("Confirm passphrase: ");
		compare(passphrase, read);
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
	
	public static char[] getPassphrase(Passphrase pp) {
	    char[] secret = PassphraseHolder.readPassword("Enter passphrase for nickname " + pp.getNickName() +" : ");
	    PassphraseHolder.setPassphrase(pp.getId(),secret);
	    return secret;
	    
	}
	

	/**
	 * Design for storing the passphrase.
	 * 
	 * @author Cesar Arevalo
	 * @see <a
	 *      href="http://en.wikipedia.org/wiki/Initialization_on_demand_holder_idiom">Initialization
	 *      on demand holder idiom</a>
	 */
	public static class PassphraseHolder {
	    
		private static Map<Integer, char[]> PASSPHRASES = new HashMap<Integer,char[]>();
		
		public static void setPassphrase(int i, char[] passphrase) {
		    PASSPHRASES.put(i, passphrase);
		}
		
		public static char[] getPassphrase() {
		    String s ="";
		    for(char[] pp : PASSPHRASES.values()) {
		        s += new String(pp);
		    }
		    return s.toCharArray();
		}
		
		public static char[] getPassphrase(int i) {
		    return PASSPHRASES.get(i);
		}

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
	
	private static final String readActivate(String message) {
	    return System.console().readLine(message);
	}
	
	private static String usage(Configuration configuration, String template, String error) {
		final StringBuilder builder = new StringBuilder();
		if (error != null) {
			builder.append("Error: ").append(error).append("\n\n");
		}
		return builder.append(template.replace("{app}", configuration.getExecutableName())).toString();
	}

}
