/**
 * 
 */
package com.wesabe.grendel.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.wesabe.grendel.GrendelRunner;

/**
 * @author Cesar Arevalo
 *
 */
public class CipherUtil {

	private static final char[] SALT = "1e54a69b3f4b93a320f3f68a6f0e6d".toCharArray();

	public static final char[] xor(String passphrase) {
		return xor(passphrase.toCharArray());
	}

	public static final char[] xor(char[] passphrase) {
		// Get a handle of the GrendelRunner passphrase
		char[] runnerPassphrase = GrendelRunner.getPassphrase();

		// Get the minimum length of either passphrase or the GrendelRunner passphrase
		int length = Math.min(passphrase.length, runnerPassphrase.length);

		// Create the char array for the XOR between passphrase and GrendelRunner passphrase
		char[] result = xor(passphrase, runnerPassphrase, length);

		// Get the minimum length of either result or salt
		length = Math.min(result.length, SALT.length);

		// XOR between the previous result and the salt
		result = xor(result, SALT, length);

		return result;
	}

	/**
	 * @param array1
	 * @param array2
	 * @param length
	 * @return
	 */
	private static char[] xor(char[] array1, char[] array2, int length) {
		char[] result = new char[length];
		for (int index = 0; index < length; index++) {
			if (index < array1.length &&
					index < array2.length) {
				result[index] = (char) (array1[index] ^ array2[index]);
			}
			else if (index < array1.length &&
					index >= array2.length) {
				result[index] = array1[index];
			}
			else if (index >= array1.length &&
					index < array2.length) {
				result[index] = array2[index];
			}
			else {
				throw new UnsupportedOperationException("There is a problem in the logic. This should NEVER happen!?");
			}
		}
		return result;
	}

	private static byte[] toByteArray(char[] charArray) {
		byte[] byteArray = new byte[charArray.length];
		for (int index = 0; index < charArray.length; index++) {
			byteArray[index] = (byte) charArray[index];
		}
		return byteArray;
	}
	
	public static byte[] createChecksum(char[] characters)
			throws NoSuchAlgorithmException {
		byte[] buffer = toByteArray(characters);
		MessageDigest complete = MessageDigest.getInstance("MD5");
		complete.update(buffer, 0, buffer.length);
		return complete.digest();

	}

	public static byte[] createChecksum(String filename) throws Exception {
		InputStream fis = new FileInputStream(filename);

		byte[] buffer = new byte[1024];
		MessageDigest complete = MessageDigest.getInstance("MD5");
		int numRead;
		do {
			numRead = fis.read(buffer);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		} while (numRead != -1);
		fis.close();
		return complete.digest();
	}

	public static String getMD5Checksum(char[] characters)
			throws Exception {
		byte[] b = createChecksum(characters);
		String result = "";
		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	public static void writeToFileAsMD5Checksum(char[] characters, String pathname) throws Exception {
		FileWriter fstream = new FileWriter(pathname);
		BufferedWriter out = new BufferedWriter(fstream);
		out.write(getMD5Checksum(characters));
		out.close();
	}
	
	public static String getContents(String pathname) throws IOException {
		File aFile = new File(pathname);
		StringBuilder contents = new StringBuilder();
		BufferedReader input = new BufferedReader(new FileReader(aFile));
		String line = null;
		while ((line = input.readLine()) != null) {
			contents.append(line);
		}
		input.close();
		return contents.toString();
	}
	
	public static boolean compareChecksum(char[] passphrase, String pathname) throws Exception {
		return getMD5Checksum(passphrase).equals(getContents(pathname));
	}
}
