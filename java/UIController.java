import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * 
 * @author CCS Team
 *
 */
@Controller
public class UIController {

	private static final Logger logger = Logger.getLogger(UIController.class);
	
	@Autowired
	ConfigProperties configProperties;

	@Autowired
	LicenseService licenseService;

	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public ModelAndView sentinel(HttpServletRequest request) {
		ModelAndView modelAndView = new ModelAndView("login");
		HttpSession session = request.getSession(true);
		String userName = request.getParameter("userName");
		String collId = request.getParameter("collId");
		return modelAndView;
	}

	@RequestMapping(value = "/login", method = RequestMethod.POST)
	public ModelAndView sentinelPost(HttpServletRequest request) {
		return sentinel(request);
	}

	/**
	 * chmod
	 * 
	 * @param filename
	 * @param mode
	 * @return method to change permissions of created files so that pull script
	 *         can rename them
	 */
	private int chmod(String filename, int mode) {
		try {
			Class<?> fspClass = Class.forName("java.util.prefs.FileSystemPreferences");
			Method chmodMethod = fspClass.getDeclaredMethod("chmod", String.class, Integer.TYPE);
			chmodMethod.setAccessible(true);
			return (Integer) chmodMethod.invoke(null, filename, mode);
		} catch (Throwable ex) {
			return -1;
		}
	}
	
	/**
	 * optionsUpload
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/fileUpload", method = RequestMethod.OPTIONS)
	public @ResponseBody ResponseEntity<Void> optionsUpload(HttpServletRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Access-Control-Allow-Origin", "*");
		return new ResponseEntity<Void>(headers, HttpStatus.OK);
	}

	/**
	 * postUpload
	 * 
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/fileUpload", method = RequestMethod.POST)
	public @ResponseBody ResponseEntity<String> postUpload(MultipartHttpServletRequest request){

		HttpHeaders headers = new HttpHeaders();
		headers.add("Access-Control-Allow-Origin", "*");
		
		int chunkNumber = Integer.parseInt(request.getParameter("resumableChunkNumber"));
		String resumableFilename = request.getParameter("resumableFilename");
		String outputPath = null;
		String userId = null;
		ResumableInfo info = null;
		try {
			info = getResumableInfo(request, outputPath);
			FileEncryption fileEncryption = new FileEncryption(info);

			if (info.getResumableFilePath() != null && info.getKey() != null) {
				outputPath = info.getResumableFilePath();
				userId = info.getUserId();
				fileEncryption.setAeskeySpec(info.getKey());
			} else { // first time creation
				String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
				int custId = Integer.valueOf(request.getParameter("custId"));
				String collId = request.getParameter("collId");
				String mappingJSON = request.getParameter("mappingJSON");
				userId = request.getParameter("userId");
				info.setUserId(userId);

				String outputDirName = custId + "-" + collId + "-"
						+ new SimpleDateFormat("MM-dd-yyyy").format(new Date()) + "-" + timeStamp;

				String uploadDir = configProperties.getUploadDir();
				String outputDir = uploadDir + outputDirName;
				outputPath = outputDir + File.separator + "outbox";
				info.setResumableFilePath(outputPath);
				String aesKeyFile = outputDir + File.separator + "AES.key";
				File publicKey = new File(uploadDir + "public.der");
				File aesKey = null;

				File mainDir = new File(outputDir);
				if (!mainDir.exists()) {
					mainDir.mkdirs();
					this.chmod(outputDir, 0777);
				}

				File subDir = new File(outputPath);
				if (!subDir.exists()) {
					subDir.mkdirs();
					this.chmod(outputPath, 0777);
				}

				aesKey = new File(aesKeyFile);
				fileEncryption.saveEncryptedKey(publicKey, aesKey);
				this.chmod(aesKeyFile, 0777);

				FileOutputStream fos = new FileOutputStream(new File(outputDir + File.separator + "mapping.json"));
				mappingJSON = URLDecoder.decode(mappingJSON, "UTF-8");
				fos.write(mappingJSON.getBytes());
				fos.close();
			}

			Iterator<String> itr = request.getFileNames();
			MultipartFile mpf = null;

			while (itr.hasNext()) {
				mpf = request.getFile(itr.next());
				InputStream is = mpf.getInputStream();

				File file = new File(outputPath + File.separator
						+ URLEncoder.encode(userId + "_" + resumableFilename, "UTF-8") + ".enc-part" + chunkNumber);

				if (!file.exists())
					file.createNewFile();

				fileEncryption.encrypt(is, file);
			}

			info.getUploadedChunks().add(new ResumableInfo.ResumableChunkNumber(chunkNumber));

			if (info.checkIfUploadFinished(outputPath, userId)) {
				ResumableInfoStorage.getInstance().remove(info);
				return new ResponseEntity<String>("The file is uploaded successfully.", headers, HttpStatus.OK);
			} else {
				return new ResponseEntity<String>("Chunk is uploaded.", headers, HttpStatus.OK);
			}
			
		} catch (Exception e) {
			if (info != null) {
				ResumableInfoStorage.getInstance().remove(info);
			}
			logger.error(e);
			e.printStackTrace();
			return new ResponseEntity<String>("There is problem in uploading the file. Please try again later.", headers, HttpStatus.INTERNAL_SERVER_ERROR);
		}

	}

	/**
	 * getUpload
	 * 
	 * @param request
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	@RequestMapping(value = "/fileUpload", method = RequestMethod.GET)
	protected ResponseEntity<String> getUpload(HttpServletRequest request){

		int resumableChunkNumber = Integer.parseInt(request.getParameter("resumableChunkNumber"));

		HttpHeaders headers = new HttpHeaders();
		headers.add("Access-Control-Allow-Origin", "*");
		
		try {  
			ResumableInfo info = getResumableInfo(request);

			if (info != null && info.getUploadedChunks()
					.contains(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber))) {
				return new ResponseEntity<String>("Chunk found.", headers, HttpStatus.OK); // This Chunk has been uploaded
			} else {
				return new ResponseEntity<String>("Chunk not found.", headers, HttpStatus.NOT_FOUND);
			}
		} catch (Exception e) {
			logger.error(e);
			e.printStackTrace();
			return new ResponseEntity<String>("Chunk not found.",  headers, HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * getResumableInfo
	 * 
	 * @param request
	 * @param outputPath
	 * @return
	 * @throws Exception
	 */
	private ResumableInfo getResumableInfo(HttpServletRequest request, String outputPath) throws Exception {

		int resumableChunkSize = Integer.parseInt(request.getParameter("resumableChunkSize"));
		long resumableTotalSize = Long.parseLong(request.getParameter("resumableTotalSize"));
		String resumableIdentifier = request.getParameter("resumableIdentifier");
		String resumableFilename = request.getParameter("resumableFilename");
		String resumableRelativePath = request.getParameter("resumableRelativePath");

		String resumableFilePath = outputPath; // custId-collId-mm-dd-yyyy-timestamp/outbox
		ResumableInfoStorage storage = ResumableInfoStorage.getInstance();
		ResumableInfo info = storage.get(resumableChunkSize, resumableTotalSize, resumableIdentifier, resumableFilename,
				resumableRelativePath, resumableFilePath);

		if (!info.vaild()) {
			storage.remove(info);
			throw new Exception("Invalid request params.");
		}

		return info;
	}

	/**
	 * getResumableInfo
	 * 
	 * @param request
	 * @return
	 * @throws Exception
	 */
	private ResumableInfo getResumableInfo(HttpServletRequest request) throws Exception {
		String resumableIdentifier = request.getParameter("resumableIdentifier");
		ResumableInfoStorage storage = ResumableInfoStorage.getInstance();
		ResumableInfo info = storage.get(resumableIdentifier);
		return info;
	}


}