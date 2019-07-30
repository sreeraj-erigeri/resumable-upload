import java.util.concurrent.ConcurrentHashMap;

public class ResumableInfoStorage {

	private static ResumableInfoStorage sInstance = new ResumableInfoStorage();
	private ConcurrentHashMap<String, ResumableInfo> mMap = new ConcurrentHashMap<String, ResumableInfo>();

	/**
	 * ResumableInfoStorage
	 */
	private ResumableInfoStorage() {}

	/**
	 * getInstance
	 * @return
	 */
	public static synchronized ResumableInfoStorage getInstance() {
		return sInstance;
	}

	/**
	 * Get ResumableInfo from mMap or Create a new one.
	 * 
	 * @param resumableChunkSize
	 * @param resumableTotalSize
	 * @param resumableIdentifier
	 * @param resumableFilename
	 * @param resumableRelativePath
	 * @param resumableFilePath
	 * @return
	 */
	public synchronized ResumableInfo get(int resumableChunkSize, long resumableTotalSize, String resumableIdentifier,
			String resumableFilename, String resumableRelativePath, String resumableFilePath) {

		ResumableInfo info = mMap.get(resumableIdentifier);

		if (info == null) {
			info = new ResumableInfo();

			info.setResumableChunkSize(resumableChunkSize);
			info.setResumableTotalSize(resumableTotalSize);
			info.setResumableIdentifier(resumableIdentifier);
			info.setResumableFilename(resumableFilename);
			info.setResumableRelativePath(resumableRelativePath);
			info.setResumableFilePath(resumableFilePath);

			mMap.put(resumableIdentifier, info);
		}
		return info;
	}

	/**
	 * get
	 * 
	 * @param resumableIdentifier
	 * @return
	 */
	public synchronized ResumableInfo get(String resumableIdentifier) {
		return mMap.get(resumableIdentifier);
	}

	/**
	 * remove
	 * 
	 * @param info
	 */
	public void remove(ResumableInfo info) {
		mMap.remove(info.getResumableIdentifier());
	}
}
