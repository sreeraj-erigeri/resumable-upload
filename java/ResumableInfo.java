import java.io.File;
import java.net.URLEncoder;
import java.util.HashSet;

import javax.crypto.spec.SecretKeySpec;

public class ResumableInfo {

	private int resumableChunkSize;
	private long resumableTotalSize;
	private String resumableIdentifier;
	private String resumableFilename;
	private String resumableRelativePath;
	private String resumableFilePath;
	private SecretKeySpec key;
	private String userId;
	private HashSet<ResumableChunkNumber> uploadedChunks = new HashSet<ResumableChunkNumber>();

	/**
	 * getKey
	 * 
	 * @return
	 */
	public SecretKeySpec getKey() {
		return key;
	}

	/**
	 * setKey
	 * 
	 * @param key
	 */
	public void setKey(SecretKeySpec key) {
		this.key = key;
	}

	/**
	 * getResumableFilePath
	 * 
	 * @return
	 */
	public String getResumableFilePath() {
		return resumableFilePath;
	}

	/**
	 * setResumableFilePath
	 * 
	 * @param resumableFilePath
	 */
	public void setResumableFilePath(String resumableFilePath) {
		this.resumableFilePath = resumableFilePath;
	}

	/**
	 * vaild
	 * 
	 * @return
	 */
	public boolean vaild() {
		if (resumableChunkSize < 0 || resumableTotalSize < 0 || HttpUtils.isEmpty(resumableIdentifier)
				|| HttpUtils.isEmpty(resumableFilename) || HttpUtils.isEmpty(resumableRelativePath)) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * checkIfUploadFinished
	 * 
	 * @param outputPath
	 * @param userId
	 * @param mappingJSON
	 * @param outputDirName
	 * @param uploadDir
	 * @return
	 */
	public boolean checkIfUploadFinished(String outputPath, String userId) {
		// check if upload finished
		int count = (int) Math.ceil(((double) resumableTotalSize) / ((double) resumableChunkSize));
		for (int i = 1; i < count; i++) {
			if (!uploadedChunks.contains(new ResumableChunkNumber(i))) {
				return false;
			}
		}

		int lastChunkNumber = count;

		try {
			File file = new File(
					outputPath + File.separator + URLEncoder.encode(userId + "_" + resumableFilename, "UTF-8")
							+ ".enc-part" + lastChunkNumber + ".eof");
			if (!file.exists())
				file.createNewFile();

			file = new File(outputPath + File.separator + "transfer.eot");

			if (!file.exists())
				file.createNewFile();
			// Upload finished, change filename.
		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}

	/**
	 * @return the resumableChunkSize
	 */
	public int getResumableChunkSize() {
		return resumableChunkSize;
	}

	/**
	 * @param resumableChunkSize
	 *            the resumableChunkSize to set
	 */
	public void setResumableChunkSize(int resumableChunkSize) {
		this.resumableChunkSize = resumableChunkSize;
	}

	/**
	 * @return the resumableTotalSize
	 */
	public long getResumableTotalSize() {
		return resumableTotalSize;
	}

	/**
	 * @param resumableTotalSize
	 *            the resumableTotalSize to set
	 */
	public void setResumableTotalSize(long resumableTotalSize) {
		this.resumableTotalSize = resumableTotalSize;
	}

	/**
	 * @return the resumableIdentifier
	 */
	public String getResumableIdentifier() {
		return resumableIdentifier;
	}

	/**
	 * @param resumableIdentifier
	 *            the resumableIdentifier to set
	 */
	public void setResumableIdentifier(String resumableIdentifier) {
		this.resumableIdentifier = resumableIdentifier;
	}

	/**
	 * @return the resumableFilename
	 */
	public String getResumableFilename() {
		return resumableFilename;
	}

	/**
	 * @param resumableFilename
	 *            the resumableFilename to set
	 */
	public void setResumableFilename(String resumableFilename) {
		this.resumableFilename = resumableFilename;
	}

	/**
	 * @return the resumableRelativePath
	 */
	public String getResumableRelativePath() {
		return resumableRelativePath;
	}

	/**
	 * @param resumableRelativePath
	 *            the resumableRelativePath to set
	 */
	public void setResumableRelativePath(String resumableRelativePath) {
		this.resumableRelativePath = resumableRelativePath;
	}

	/**
	 * @return the uploadedChunks
	 */
	public HashSet<ResumableChunkNumber> getUploadedChunks() {
		return uploadedChunks;
	}

	/**
	 * @param uploadedChunks
	 *            the uploadedChunks to set
	 */
	public void setUploadedChunks(HashSet<ResumableChunkNumber> uploadedChunks) {
		this.uploadedChunks = uploadedChunks;
	}

	/**
	 * @return the userId
	 */
	public String getUserId() {
		return userId;
	}

	/**
	 * @param userId
	 *            the userId to set
	 */
	public void setUserId(String userId) {
		this.userId = userId;
	}

	/**
	 * ResumableChunkNumber
	 */
	public static class ResumableChunkNumber {
		public ResumableChunkNumber(int number) {
			this.number = number;
		}

		public int number;

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ResumableChunkNumber ? ((ResumableChunkNumber) obj).number == this.number : false;
		}

		@Override
		public int hashCode() {
			return number;
		}
	}
}
