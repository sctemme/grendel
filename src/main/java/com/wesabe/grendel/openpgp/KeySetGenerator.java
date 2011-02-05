package com.wesabe.grendel.openpgp;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.joda.time.DateTime;

import com.wesabe.grendel.util.CipherUtil;
import com.google.inject.Inject;
import com.google.inject.internal.ImmutableList;
import com.wesabe.grendel.util.IntegerEquivalents;

/**
 * A multithreaded generator for {@link KeySet}s.
 * 
 * Generates master keys using {@link AsymmetricAlgorithm#ENCRYPTION_DEFAULT},
 * and subkeys using {@link AsymmetricAlgorithm#SIGNING_DEFAULT}.
 * 
 * @author coda
 */
public class KeySetGenerator {
	
	/**
	 * A {@link Callable} which returns a new key pair.
	 * @author coda
	 *
	 */
	private static class GeneratorTask implements Callable<KeyPair> {
		private final AsymmetricAlgorithm algorithm;
		private final SecureRandom random;
		
		public GeneratorTask(AsymmetricAlgorithm algorithm, SecureRandom random) {
			this.algorithm = algorithm;
			this.random = random;
		}
		
		@Override
		public KeyPair call() throws Exception {
			final KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm.getName(), "BC");
			generator.initialize(algorithm.getAlgorithmParameterSpec(), random);
			return generator.generateKeyPair();
		}
	}
	
	/**
	 * @author Cesar Arevalo
	 */
	public static class KeyPairHolder {
		private static final SecureRandom RANDOM = new SecureRandom();
		private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
		public static final KeyPair MASTER_KEY_PAIR = getMasterKeyPair();
		public static final KeyPair SUB_KEY_PAIR = getSubKeyPair();
		
		private static KeyPair getMasterKeyPair() {
			if (MASTER_KEY_PAIR == null) {
				try {
					return EXECUTOR.submit(new GeneratorTask(AsymmetricAlgorithm.SIGNING_DEFAULT, RANDOM)).get();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
			}
			return MASTER_KEY_PAIR;
		}
		
		private static KeyPair getSubKeyPair() {
			if (SUB_KEY_PAIR == null) {
				try {
					return EXECUTOR.submit(new GeneratorTask(AsymmetricAlgorithm.ENCRYPTION_DEFAULT, RANDOM)).get();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
			}
			return SUB_KEY_PAIR;
		}
	}
	
	/**
	 * Creates a new {@link KeySetGenerator}.
	 * 
	 * @param random a secure random number generator
	 */
	@Inject
	public KeySetGenerator(SecureRandom random) {
	}
	
	/**
	 * Generates a new {@link KeySet}.
	 * 
	 * @param userId the user ID, in {@code First Last <email@example.com>} format
	 * @param passphrase the user's passphrase
	 * @return a keyset for the user
	 * @throws CryptographicException if there was an error generating the keyset
	 */
	public KeySet generate(String userId, char[] passphrase) throws CryptographicException {
		try {
			final PGPKeyPair masterPGPKeyPair = new PGPKeyPair(
				AsymmetricAlgorithm.SIGNING_DEFAULT.toInteger(),
				KeyPairHolder.MASTER_KEY_PAIR,
				new DateTime().toDate()
			);

			final PGPKeyRingGenerator generator = new PGPKeyRingGenerator(
				SignatureType.POSITIVE_CERTIFICATION.toInteger(),
				masterPGPKeyPair,
				userId,
				SymmetricAlgorithm.DEFAULT.toInteger(),
				CipherUtil.xor(passphrase),
				true, // use SHA-1 instead of MD5
				generateMasterKeySettings(),
				null, // don't store any key settings unhashed
				KeyPairHolder.RANDOM,
				"BC"
			);

			final PGPKeyPair subPGPKeyPair = new PGPKeyPair(
				AsymmetricAlgorithm.ENCRYPTION_DEFAULT.toInteger(),
				KeyPairHolder.SUB_KEY_PAIR,
				new DateTime().toDate()
			);

			generator.addSubKey(subPGPKeyPair, generateSubKeySettings(), null);

			final PGPSecretKeyRing keyRing = generator.generateSecretKeyRing();
			return KeySet.load(keyRing);
			
		} catch (GeneralSecurityException e) {
			throw new CryptographicException(e);
		} catch (PGPException e) {
			throw new CryptographicException(e);
		}
	}

	private PGPSignatureSubpacketVector generateSubKeySettings() {
		final PGPSignatureSubpacketGenerator settings = new PGPSignatureSubpacketGenerator();
		settings.setKeyFlags(false, IntegerEquivalents.toBitmask(KeyFlag.SUB_KEY_DEFAULTS));
		return settings.generate();
	}

	private PGPSignatureSubpacketVector generateMasterKeySettings() {
		final PGPSignatureSubpacketGenerator settings = new PGPSignatureSubpacketGenerator();
		settings.setKeyFlags(false,
			IntegerEquivalents.toBitmask(KeyFlag.MASTER_KEY_DEFAULTS)
		);
		settings.setPreferredSymmetricAlgorithms(false,
			IntegerEquivalents.toIntArray(SymmetricAlgorithm.ACCEPTABLE_ALGORITHMS)
		);
		settings.setPreferredHashAlgorithms(false,
			IntegerEquivalents.toIntArray(HashAlgorithm.ACCEPTABLE_ALGORITHMS)
		);
		settings.setPreferredCompressionAlgorithms(false,
				IntegerEquivalents.toIntArray(
					ImmutableList.of(
						CompressionAlgorithm.BZIP2,
						CompressionAlgorithm.ZLIB,
						CompressionAlgorithm.ZIP
					)
				)
		);
		return settings.generate();
	}
}
