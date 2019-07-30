import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * 
 * @author CCS Team
 *
 */
public class DecryptAndMergeUtility {

	public static final int AES_Key_Size = 128;
	Cipher pkCipher, aesCipher;
	byte[] aesKey;
	SecretKeySpec aeskeySpec;

	/**
	 * Constructor
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 */
	public DecryptAndMergeUtility() throws NoSuchPaddingException, NoSuchAlgorithmException {
		pkCipher = Cipher.getInstance("RSA");
		aesCipher = Cipher.getInstance("AES/CFB8/NoPadding");
	}

	/**
	 * Usage: java -jar DecryptAndMergeUtility.jar [input path] [private key]
	 * @param args
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public static void main(String args[]) throws GeneralSecurityException, IOException {

		if (args.length < 2) {
        	System.out.println("Usage:   java -jar DecryptAndMergeUtility.jar [input path] [private key]");
        	System.out.println("Example: java -jar DecryptAndMergeUtility.jar /hdfs/app/CloudConsumption/CCPdata/input/XX-SNTL-CXXXX-mm-dd-yyyy-XXXXXXX /hdfs/app/CloudConsumption/CSCOccp/data/incoming/saas/private.der");
        	return;
        }

		String inputDirectory = args[0];
		String privatekey = args[1];

		DecryptAndMergeUtility decryptAndMergeUtility = new DecryptAndMergeUtility();
		List<String> originalFileNameList = new ArrayList<String>();
		Map<String, List<String>> fianlFileNames = new HashMap<String, List<String>>();

		String mappingFileInput = inputDirectory + "/mapping.json";
		InputStream mapInputStream = new FileInputStream(new File(mappingFileInput));

		String encryptedFileDir = inputDirectory + "/outbox";

		List<String> encryptedFilesList = decryptAndMergeUtility.listFiles(encryptedFileDir);
		for (String fileName : encryptedFilesList) {
			if (fileName.endsWith(".eof")) {
				String originalFileName = fileName.substring(0, fileName.indexOf(".enc-part"));
				originalFileNameList.add(originalFileName);
			}
		}

		for (String fileName : encryptedFilesList) {
			if (!fileName.endsWith(".eof") && !fileName.endsWith(".eot")) {
				String encrytedFilePartName = fileName.substring(0, fileName.indexOf(".enc-part"));
				if (originalFileNameList.contains(encrytedFilePartName)) {
					if (fianlFileNames.containsKey(encrytedFilePartName)) {

						List<String> filesList = fianlFileNames.get(encrytedFilePartName);
						filesList.add(fileName);
						fianlFileNames.put(encrytedFilePartName, filesList);
					} else {
						List<String> filesList = new ArrayList<String>();
						filesList.add(fileName);
						fianlFileNames.put(encrytedFilePartName, filesList);
					}
				}
			}
		}

		File aesKey = new File(inputDirectory + "/" + "AES.key");
		File privateKey = new File(privatekey);
		decryptAndMergeUtility.loadKey(aesKey, privateKey);

		for (String finalDecrytedFile : fianlFileNames.keySet()) {
			List<String> finalFilesToDecrypt = fianlFileNames.get(finalDecrytedFile);
			Collections.sort(finalFilesToDecrypt, CustomComparator);

			File finalFile = new File(inputDirectory + "/processed/data/" + finalDecrytedFile);

			if (!finalFile.getParentFile().exists()) {
				System.out.println();
				finalFile.getParentFile().mkdirs();
			}

			File mappingFileOutput = new File(inputDirectory + "/" + "processed/mapping.json");
			if (!mappingFileOutput.exists()) {
				mappingFileOutput.createNewFile();
				OutputStream mapOutputStream = new FileOutputStream(mappingFileOutput);
				decryptAndMergeUtility.copy(mapInputStream, mapOutputStream);
			}

			if (!finalFile.exists()) {
				System.out.println("File Created : " + finalFile.getAbsolutePath());
				finalFile.createNewFile();
				OutputStream outputFileStream = new FileOutputStream(finalFile, true);
				for (String fileName : finalFilesToDecrypt) {
					File inputEncrytedPartFile = new File(inputDirectory + "/outbox/" + fileName);
					decryptAndMergeUtility.decrypt(inputEncrytedPartFile, outputFileStream);
				}
				outputFileStream.close();
			}
		}
		fianlFileNames.clear();
	}

    /**
     * decrypt
     * @param in
     * @param os
     * @throws IOException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     */
	private void decrypt(File in, OutputStream os)
			throws IOException, InvalidKeyException, InvalidAlgorithmParameterException {
		byte[] iv = new byte[aesCipher.getBlockSize()];
		AlgorithmParameterSpec spec = new IvParameterSpec(iv);
		aesCipher.init(Cipher.DECRYPT_MODE, aeskeySpec, spec);
		CipherInputStream is = new CipherInputStream(new FileInputStream(in), aesCipher);

		copy(is, os);
		is.close();
	}

	/**
	 * copy
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	private void copy(InputStream is, OutputStream os) throws IOException {
		int i;
		byte[] b = new byte[8 * 1024];
		while ((i = is.read(b)) != -1) {
			os.write(b, 0, i);
		}
	}

	/**
	 * loadKey
	 * @param in
	 * @param privateKeyFile
	 * @throws GeneralSecurityException
	 * @throws IOException
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
	 * listDirectories
	 * @param directory
	 * @return
	 */
	public List<String> listDirectories(String directory) {
		File dir = new File(directory);
		String[] directories = dir.list(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				return new File(current, name).isDirectory();
			}
		});
		List<String> dirList = Arrays.asList(directories);
		return dirList;
	}

	/**
	 * listFiles
	 * @param directory
	 * @return
	 */
	public List<String> listFiles(String directory) {
		System.out.println("Directory - " + directory);
		List<String> fileNames = new ArrayList<String>();
		File[] files = new File(directory).listFiles();
		for (File file : files) {
			if (file.isFile()) {
				fileNames.add(file.getName());
			}
		}
		return fileNames;
	}

	/**
	 * Comparator
	 */
	public static Comparator<String> CustomComparator = new Comparator<String>() {
		@Override
		public int compare(String e1, String e2) {
			return Integer.parseInt(e1.substring(e1.indexOf("enc-part") + 8))
					- Integer.parseInt(e2.substring(e1.indexOf("enc-part") + 8));
		}
	};

}
