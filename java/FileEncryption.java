import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class FileEncryption {

	public static final int AES_Key_Size = 256;

	Cipher pkCipher, aesCipher;
	byte[] aesKey;
	SecretKeySpec aeskeySpec;
	ResumableInfo info;

	public SecretKeySpec getAeskeySpec() {
		return aeskeySpec;
	}

	public void setAeskeySpec(SecretKeySpec aeskeySpec) {
		this.aeskeySpec = aeskeySpec;
	}
	
	/**
	 * FileEncryption
	 * @throws GeneralSecurityException
	 * Constructor: creates ciphers
	 */
	public FileEncryption() throws GeneralSecurityException {
		// create RSA public key cipher
		pkCipher = Cipher.getInstance("RSA");
		// create AES shared key cipher
		aesCipher = Cipher.getInstance("AES");
	}
	
	/**
	 * FileEncryption
	 * @param info
	 * @throws GeneralSecurityException
	 */
	public FileEncryption(ResumableInfo info) throws GeneralSecurityException {
		this.info = info;
		// create RSA public key cipher
		pkCipher = Cipher.getInstance("RSA");
		// create AES shared key cipher
		aesCipher = Cipher.getInstance("AES/CFB8/NoPadding");
	}

	/**
	 * Creates a new AES key
	 */
	public void makeKey() throws NoSuchAlgorithmException {
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		kgen.init(AES_Key_Size);
		SecretKey key = kgen.generateKey();
		aesKey = key.getEncoded();
		aeskeySpec = new SecretKeySpec(aesKey, "AES");
		info.setKey(aeskeySpec);
	}
	
	/**
	 * loadKey
	 * @param in
	 * @param privateKeyFile
	 * @throws GeneralSecurityException
	 * @throws IOException
	 * Decrypts an AES key from a file using an RSA private key
	 */
	public void loadKey(File in, File privateKeyFile) throws GeneralSecurityException, IOException {
		// read private key to be used to decrypt the AES key
		byte[] encodedKey = new byte[(int) privateKeyFile.length()];
		FileInputStream fis = new FileInputStream(privateKeyFile);
		fis.read(encodedKey);
		fis.close();
		
		// create private key
		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedKey);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey pk = kf.generatePrivate(privateKeySpec);

		// read AES key
		pkCipher.init(Cipher.DECRYPT_MODE, pk);
		aesKey = new byte[AES_Key_Size / 8];

		CipherInputStream is = new CipherInputStream(new FileInputStream(in), pkCipher);
		is.read(aesKey);
		is.close();

		aeskeySpec = new SecretKeySpec(aesKey, "AES");
	}

	/**
	 * loadKey
	 * @param in
	 * @param privateKeyFile
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public void loadKey(InputStream in, File privateKeyFile) throws GeneralSecurityException, IOException {
		// read private key to be used to decrypt the AES key
		byte[] encodedKey = new byte[(int) privateKeyFile.length()];
		FileInputStream fis = new FileInputStream(privateKeyFile);
		fis.read(encodedKey);
		fis.close();
		
		// create private key
		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedKey);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey pk = kf.generatePrivate(privateKeySpec);

		// read AES key
		pkCipher.init(Cipher.DECRYPT_MODE, pk);
		aesKey = new byte[AES_Key_Size / 8];
		CipherInputStream is = new CipherInputStream(in, pkCipher);
		is.read(aesKey);
		is.close();
		
		aeskeySpec = new SecretKeySpec(aesKey, "AES");
	}

	/**
	 * saveKey
	 * @param out
	 * @param publicKeyFile
	 * @throws IOException
	 * @throws GeneralSecurityException
	 * Encrypts the AES key to a file using an RSA public key
	 */
	public void saveKey(File out, File publicKeyFile) throws IOException, GeneralSecurityException {
		// read public key to be used to encrypt the AES key
		byte[] encodedKey = new byte[(int) publicKeyFile.length()];
		FileInputStream fis = new FileInputStream(publicKeyFile);
		fis.read(encodedKey);
		fis.close();
		
		// create public key
		X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedKey);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PublicKey pk = kf.generatePublic(publicKeySpec);

		// write AES key
		pkCipher.init(Cipher.ENCRYPT_MODE, pk);
		CipherOutputStream os = new CipherOutputStream(new FileOutputStream(out), pkCipher);
		os.write(aesKey);
		os.close();
	}
	
	/**
	 * saveEncryptedKey
	 * @param publicKey
	 * @param aesKey
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public void saveEncryptedKey(File publicKey, File aesKey) throws IOException, GeneralSecurityException {
		makeKey();
		saveKey(aesKey, publicKey);
	}

	/**
	 * encrypt
	 * @param buffer
	 * @param out
	 * @throws IOException
	 */
	public void encrypt(byte[] buffer, OutputStream out) throws IOException {
		try {
			byte[] iv = new byte[aesCipher.getBlockSize()];
			AlgorithmParameterSpec spec = new IvParameterSpec(iv);
			aesCipher.init(Cipher.ENCRYPT_MODE, aeskeySpec, spec);
			byte[] dataEnc = aesCipher.doFinal(buffer);
			out.write(dataEnc);

		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} finally {
			out.close();
		}
	}

	/**
	 * encrypt
	 * @param is
	 * @param out
	 * @throws IOException
	 * @throws InvalidKeyException
	 */
	public void encrypt(InputStream is, File out) throws IOException, InvalidKeyException {
		byte[] iv = new byte[aesCipher.getBlockSize()];
		AlgorithmParameterSpec spec = new IvParameterSpec(iv);
		try {
			aesCipher.init(Cipher.ENCRYPT_MODE, aeskeySpec, spec);
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		}
		CipherOutputStream os = new CipherOutputStream(new FileOutputStream(out), aesCipher);

//		FileOutputStream os = new FileOutputStream(out);
		copy(is, os);
		is.close();
		os.close();
	}

	/**
	 * encrypt
	 * @param in
	 * @param out
	 * @throws IOException
	 * @throws InvalidKeyException
	 */
	public void encrypt(File in, File out) throws IOException, InvalidKeyException {
		aesCipher.init(Cipher.ENCRYPT_MODE, aeskeySpec);

		FileInputStream is = new FileInputStream(in);
		CipherOutputStream os = new CipherOutputStream(new FileOutputStream(out), aesCipher);

		copy(is, os);
		is.close();
		os.close();
	}

	/**
	 * Decrypts and then copies the contents of a given file.
	 */
	public void decrypt(File in, File out) {
		try {
			byte[] iv = new byte[aesCipher.getBlockSize()];
			AlgorithmParameterSpec spec = new IvParameterSpec(iv);
			aesCipher.init(Cipher.DECRYPT_MODE, aeskeySpec, spec);
			byte[] buffer = new byte[8 * 1024];
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(in), buffer.length);
			FileOutputStream os = new FileOutputStream(out);
			while (bis.read(buffer) != -1) {
				byte[] decrypted = aesCipher.doFinal(buffer);
				os.write(decrypted);
				buffer = new byte[8 * 1024];
			}

			bis.close();
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		}
	}

	/**
	 * copy
	 * @param is
	 * @param os
	 * @throws IOException
	 * Copies a stream.
	 */
	private void copy(InputStream is, OutputStream os) throws IOException {
		int i;
		byte[] b = new byte[8 * 1024];
		while ((i = is.read(b)) != -1) {
			os.write(b, 0, i);
		}
	}
	
	/**
	 * channelCopy
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	private void channelCopy(InputStream is, OutputStream os) throws IOException {
		ReadableByteChannel src = Channels.newChannel(is);
		WritableByteChannel dest = Channels.newChannel(os);

		final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
	    while (src.read(buffer) != -1) {
	        buffer.flip();
	        dest.write(buffer);
	        buffer.compact();
	    }
	    buffer.flip();
	    while (buffer.hasRemaining()) {
	        dest.write(buffer);
	    }
	}

	/**
	 * AlgorithmParameterSpec
	 * @return
	 */
	public AlgorithmParameterSpec getIV() {
		AlgorithmParameterSpec ivspec;
		byte[] iv = new byte[aesCipher.getBlockSize()];
		new SecureRandom().nextBytes(iv);
		ivspec = new IvParameterSpec(iv);
		return ivspec;
	}
}
