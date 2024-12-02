package io.mosip.print.service.impl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.mosip.vercred.CredentialsVerifier;
import io.mosip.vercred.exception.ProofDocumentNotFoundException;
import io.mosip.vercred.exception.ProofTypeNotFoundException;
import io.mosip.vercred.exception.PubicKeyNotFoundException;
import io.mosip.vercred.exception.UnknownException;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import io.mosip.print.constant.CredentialStatusConstant;
import io.mosip.print.constant.EventId;
import io.mosip.print.constant.EventName;
import io.mosip.print.constant.EventType;
import io.mosip.print.constant.IdType;
import io.mosip.print.constant.ModuleName;
import io.mosip.print.constant.PDFGeneratorExceptionCodeConstant;
import io.mosip.print.constant.PlatformSuccessMessages;
import io.mosip.print.constant.QrVersion;
import io.mosip.print.constant.UinCardType;
import io.mosip.print.dto.CryptoWithPinRequestDto;
import io.mosip.print.dto.CryptoWithPinResponseDto;
import io.mosip.print.dto.DataShare;
import io.mosip.print.dto.JsonValue;
import io.mosip.print.exception.ApiNotAccessibleException;
import io.mosip.print.exception.ApisResourceAccessException;
import io.mosip.print.exception.CryptoManagerException;
import io.mosip.print.exception.DataShareException;
import io.mosip.print.exception.ExceptionUtils;
import io.mosip.print.exception.IdRepoAppException;
import io.mosip.print.exception.IdentityNotFoundException;
import io.mosip.print.exception.PDFGeneratorException;
import io.mosip.print.exception.PDFSignatureException;
import io.mosip.print.exception.ParsingException;
import io.mosip.print.exception.PlatformErrorMessages;
import io.mosip.print.exception.QrcodeGenerationException;
import io.mosip.print.exception.TemplateProcessingFailureException;
import io.mosip.print.exception.UINNotFoundInDatabase;
import io.mosip.print.logger.LogDescription;
import io.mosip.print.logger.PrintLogger;
import io.mosip.print.model.CredentialStatusEvent;
import io.mosip.print.model.EventModel;
import io.mosip.print.model.StatusEvent;
import io.mosip.print.service.PrintService;
import io.mosip.print.service.UinCardGenerator;
import io.mosip.print.spi.CbeffUtil;
import io.mosip.print.spi.QrCodeGenerator;
import io.mosip.print.util.AuditLogRequestBuilder;
import io.mosip.print.util.CbeffToBiometricUtil;
import io.mosip.print.util.CryptoCoreUtil;
import io.mosip.print.util.CryptoUtil;
import io.mosip.print.util.DataShareUtil;
import io.mosip.print.util.DateUtils;
import io.mosip.print.util.JsonUtil;
import io.mosip.print.util.RestApiClient;
import io.mosip.print.util.TemplateGenerator;
import io.mosip.print.util.Utilities;
import io.mosip.print.util.WebSubSubscriptionHelper;
import io.mosip.vercred.CredentialsVerifier;

@Service
public class PrintServiceImpl implements PrintService {

	private static String topic="CREDENTIAL_STATUS_UPDATE";
	private static int passwordLengthPerAttribute=4;
	
	@Autowired
	private WebSubSubscriptionHelper webSubSubscriptionHelper;

	@Autowired
	private DataShareUtil dataShareUtil;

	@Autowired
	CryptoUtil cryptoUtil;

	@Autowired
	private RestApiClient restApiClient;

	@Autowired
	private CryptoCoreUtil cryptoCoreUtil;

	/** The Constant FILE_SEPARATOR. */
	public static final String FILE_SEPARATOR = File.separator;

	/** The Constant VALUE. */
	private static final String VALUE = "value";

	/** The Constant UIN_CARD_TEMPLATE. */
	private static final String UIN_CARD_TEMPLATE = "RPR_UIN_CARD_TEMPLATE";

	/** The Constant FACE. */
	private static final String FACE = "Face";

	/** The Constant UIN_TEXT_FILE. */
	private static final String UIN_TEXT_FILE = "textFile";

	/** The Constant APPLICANT_PHOTO. */
	private static final String APPLICANT_PHOTO = "ApplicantPhoto";

	/** The Constant QRCODE. */
	private static final String QRCODE = "QrCode";

	/** The Constant UINCARDPASSWORD. */
	private static final String UINCARDPASSWORD = "mosip.print.service.uincard.password";

	/** The print logger. */
	Logger printLogger = PrintLogger.getLogger(PrintServiceImpl.class);

	/** The core audit request builder. */
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	/** The template generator. */
	@Autowired
	private TemplateGenerator templateGenerator;

	/** The utilities. */
	@Autowired
	private Utilities utilities;

	/** The uin card generator. */
	@Autowired
	private UinCardGenerator<byte[]> uinCardGenerator;

	/** The qr code generator. */
	@Autowired
	private QrCodeGenerator<QrVersion> qrCodeGenerator;

	/** The cbeffutil. */
	@Autowired
	private CbeffUtil cbeffutil;

	/** The env. */
	@Autowired
	private Environment env;

	@Autowired
	private CredentialsVerifier credentialsVerifier;

	@Value("${mosip.datashare.partner.id}")
	private String partnerId;

	@Value("${mosip.datashare.policy.id}")
	private String policyId;

	@Value("${mosip.template-language}")
	private String templateLang;

	@Value("#{'${mosip.mandatory-languages:}'.concat('${mosip.optional-languages:}')}")
	private String supportedLang;

	@Value("${mosip.print.verify.credentials.flag:true}")
	private boolean verifyCredentialsFlag;

    @Value("${mosip.print.service.uincard.pdf.password.enable:false}")
    private boolean isPasswordProtected;

	@Override
    public boolean generateCard(EventModel eventModel) {
        boolean isPrinted = false;
        try {
            printStatusUpdate(eventModel.getEvent().getTransactionId(), CredentialStatusConstant.RECEIVED.name(), null);
            String credential = getCredential(eventModel);
            String decodedCredential = decryptCredential(credential);
            printLogger.debug("vc is printed security valuation.... : {}", decodedCredential);
            if (!hasPrintCredentialVerified(eventModel, decodedCredential)) {
				return false;
			}
            byte[] pdfbytes = getDocuments(decodedCredential,
                    eventModel.getEvent().getData().get("credentialType").toString(), eventModel.getEvent().getData().get("protectionKey").toString(),
                    eventModel.getEvent().getTransactionId(), isPasswordProtected).get("uinPdf");
            isPrinted = true;
        } catch (Exception e) {
            printLogger.error(e.getMessage(), e);
            isPrinted = false;
        }
        return isPrinted;
    }

    /**
     * Decrypts print credential using MOSIP's decryption logic in ref. impl.
     * Print partner system has to use their own decryption logic to decrypt the credential.
     * @param credential
     * @return
     */
    private String decryptCredential(String credential) {
        return cryptoCoreUtil.decrypt(credential);
    }

    /**
     * Verifies Print credentials using credential verifier.
     * @param eventModel
     * @param decodedCredential
     * @return
     */
    private boolean hasPrintCredentialVerified(EventModel eventModel, String decodedCredential) {

        if (verifyCredentialsFlag) {
            printLogger.info("Configured received credentials to be verified. Flag {}", verifyCredentialsFlag);
            try {
                boolean verified = credentialsVerifier.verifyPrintCredentials(decodedCredential);
                if (!verified) {
                    printLogger.error("Received Credentials failed in verifiable credential verify method. So, the credentials will not be printed." +
                            " Id: {}, Transaction Id: {}", eventModel.getEvent().getId(), eventModel.getEvent().getTransactionId());
                    return false;
                }
            } catch (ProofDocumentNotFoundException | ProofTypeNotFoundException e) {
                printLogger.error("Proof document is not available in the received credentials." +
                        " Id: {}, Transaction Id: {}", eventModel.getEvent().getId(), eventModel.getEvent().getTransactionId());
                return false;
            } catch (UnknownException | PubicKeyNotFoundException e) {
                printLogger.error("Received Credentials failed in verifiable credential verify method. So, the credentials will not be printed." +
                        " Id: {}, Transaction Id: {}", eventModel.getEvent().getId(), eventModel.getEvent().getTransactionId());
                return false;
            }
        }
        return true;
    }

    /**
     * Fetch credential from the event if not using datashare URL.
     * @param eventModel
     * @return
     * @throws Exception
     */
    private String getCredential(EventModel eventModel) throws Exception {
        String credential;
        if (eventModel.getEvent().getDataShareUri() == null || eventModel.getEvent().getDataShareUri().isEmpty()) {
            credential = eventModel.getEvent().getData().get("credential").toString();
        } else {
            String dataShareUrl = eventModel.getEvent().getDataShareUri();
            URI dataShareUri = URI.create(dataShareUrl);
            credential = restApiClient.getApi(dataShareUri, String.class);
        }
        return credential;
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.print.service.PrintService#
	 * getDocuments(io.mosip.registration.processor.core.constant.IdType,
	 * java.lang.String, java.lang.String, boolean)
	 */
	private Map<String, byte[]> getDocuments(String credential, String credentialType, String encryptionPin,
			String requestId, boolean isPasswordProtected) {
		printLogger.debug("PrintServiceImpl::getDocuments()::entry");
		String credentialSubject;
		Map<String, byte[]> byteMap = new HashMap<>();
		String uin = null;
		LogDescription description = new LogDescription();
		String password = null;
		boolean isPhotoSet=false;
		String individualBio = null;
		Map<String, Object> attributes = new LinkedHashMap<>();
		boolean isTransactionSuccessful = false;
		String template = UIN_CARD_TEMPLATE;
		byte[] pdfbytes = null;
		boolean isQRcodeSet=false;
		try {

			credentialSubject = getCrdentialSubject(credential);
			org.json.JSONObject credentialSubjectJson = new org.json.JSONObject(credentialSubject);
			org.json.JSONObject decryptedJson = decryptAttribute(credentialSubjectJson, encryptionPin, credential);
			if(decryptedJson.has("biometrics")){
				individualBio = decryptedJson.getString("biometrics");
				String individualBiometric = new String(individualBio);
				isPhotoSet = setApplicantPhoto(individualBiometric, attributes);
				attributes.put("isPhotoSet",isPhotoSet);
			}
			uin = decryptedJson.getString("UIN");
			if (isPasswordProtected) {
				password = getPassword(decryptedJson);
			}
			if (credentialType.equalsIgnoreCase("qrcode")) {
				isQRcodeSet = setQrCode(decryptedJson.toString(), attributes,isPhotoSet);
				InputStream uinArtifact = templateGenerator.getTemplate(template, attributes, templateLang);
				pdfbytes = uinCardGenerator.generateUinCard(uinArtifact, UinCardType.PDF,
						password);

			} else {
			if (!isPhotoSet) {
				printLogger.debug(PlatformErrorMessages.PRT_PRT_APPLICANT_PHOTO_NOT_SET.name());
			}
			setTemplateAttributes(decryptedJson.toString(), attributes);
			attributes.put(IdType.UIN.toString(), uin);
			byte[] textFileByte = createTextFile(decryptedJson.toString());
			byteMap.put(UIN_TEXT_FILE, textFileByte);
			if (credentialType.equalsIgnoreCase("eUIN_with_faceQR")) {
				isQRcodeSet = setFaceQrCode(decryptedJson.toString(), attributes, isPhotoSet);
			}else{
				isQRcodeSet = setQrCode(decryptedJson.toString(), attributes, isPhotoSet);
			}
			if (!isQRcodeSet) {
				printLogger.debug(PlatformErrorMessages.PRT_PRT_QRCODE_NOT_SET.name());
			}
			// getting template and placing original valuespng
			InputStream uinArtifact = templateGenerator.getTemplate(template, attributes, templateLang);
			if (uinArtifact == null) {
				printLogger.error(PlatformErrorMessages.PRT_TEM_PROCESSING_FAILURE.name());
				throw new TemplateProcessingFailureException(
						PlatformErrorMessages.PRT_TEM_PROCESSING_FAILURE.getCode());
			}
			pdfbytes = uinCardGenerator.generateUinCard(uinArtifact, UinCardType.PDF, password);

		}
			String datashareUrl = getDatashareUrl(pdfbytes);
			printStatusUpdate(requestId, CredentialStatusConstant.PRINTED.name(), datashareUrl);
			isTransactionSuccessful = true;

		}
		catch (QrcodeGenerationException e) {
			description.setMessage(PlatformErrorMessages.PRT_PRT_QR_CODE_GENERATION_ERROR.getMessage());
			description.setCode(PlatformErrorMessages.PRT_PRT_QR_CODE_GENERATION_ERROR.getCode());
			printLogger.error(PlatformErrorMessages.PRT_PRT_QRCODE_NOT_GENERATED.name() , e);
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getErrorText());

		} catch (UINNotFoundInDatabase e) {
			description.setMessage(PlatformErrorMessages.PRT_PRT_UIN_NOT_FOUND_IN_DATABASE.getMessage());
			description.setCode(PlatformErrorMessages.PRT_PRT_UIN_NOT_FOUND_IN_DATABASE.getCode());

			printLogger.error(
					PlatformErrorMessages.PRT_PRT_UIN_NOT_FOUND_IN_DATABASE.name() ,e);
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getErrorText());

		} catch (TemplateProcessingFailureException e) {
			description.setMessage(PlatformErrorMessages.PRT_TEM_PROCESSING_FAILURE.getMessage());
			description.setCode(PlatformErrorMessages.PRT_TEM_PROCESSING_FAILURE.getCode());

			printLogger.error(PlatformErrorMessages.PRT_TEM_PROCESSING_FAILURE.name() ,e);
			throw new TemplateProcessingFailureException(PlatformErrorMessages.PRT_TEM_PROCESSING_FAILURE.getMessage());

		} catch (PDFGeneratorException e) {
			description.setMessage(PlatformErrorMessages.PRT_PRT_PDF_NOT_GENERATED.getMessage());
			description.setCode(PlatformErrorMessages.PRT_PRT_PDF_NOT_GENERATED.getCode());

			printLogger.error(PlatformErrorMessages.PRT_PRT_PDF_NOT_GENERATED.name() ,e);
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getErrorText());

		} catch (PDFSignatureException e) {
			description.setMessage(PlatformErrorMessages.PRT_PRT_PDF_SIGNATURE_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.PRT_PRT_PDF_SIGNATURE_EXCEPTION.getCode());

			printLogger.error(PlatformErrorMessages.PRT_PRT_PDF_SIGNATURE_EXCEPTION.name() ,e);
			throw new PDFSignatureException(PlatformErrorMessages.PRT_PRT_PDF_SIGNATURE_EXCEPTION.getMessage());

		} catch (Exception ex) {
			description.setMessage(PlatformErrorMessages.PRT_PRT_PDF_GENERATION_FAILED.getMessage());
			description.setCode(PlatformErrorMessages.PRT_PRT_PDF_GENERATION_FAILED.getCode());
			printLogger.error(ex.getMessage() ,ex);
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					ex.getMessage() ,ex);
		} finally {
			String eventId = "";
			String eventName = "";
			String eventType = "";
			if (isTransactionSuccessful) {
				description.setMessage(PlatformSuccessMessages.RPR_PRINT_SERVICE_SUCCESS.getMessage());
				description.setCode(PlatformSuccessMessages.RPR_PRINT_SERVICE_SUCCESS.getCode());

				eventId = EventId.RPR_402.toString();
				eventName = EventName.UPDATE.toString();
				eventType = EventType.BUSINESS.toString();
			} else {
				printStatusUpdate(requestId, CredentialStatusConstant.ERROR.name(), null);
				description.setMessage(PlatformErrorMessages.PRT_PRT_PDF_GENERATION_FAILED.getMessage());
				description.setCode(PlatformErrorMessages.PRT_PRT_PDF_GENERATION_FAILED.getCode());

				eventId = EventId.RPR_405.toString();
				eventName = EventName.EXCEPTION.toString();
				eventType = EventType.SYSTEM.toString();
			}
			/** Module-Id can be Both Success/Error code */
			String moduleId = isTransactionSuccessful ? PlatformSuccessMessages.RPR_PRINT_SERVICE_SUCCESS.getCode()
					: description.getCode();
			String moduleName = ModuleName.PRINT_SERVICE.toString();
			auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
					moduleId, moduleName, uin);
		}
		printLogger.debug("PrintServiceImpl::getDocuments()::exit");

		return byteMap;
	}

    private String getDatashareUrl(byte[] data) throws IOException, DataShareException, ApiNotAccessibleException {
        return dataShareUtil.getDataShare(data, policyId, partnerId).getUrl();
    }

	/**
	 * Creates the text file.
	 *
	 * @param jsonString
	 *            the attributes
	 * @return the byte[]
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private byte[] createTextFile(String jsonString) throws IOException {

		LinkedHashMap<String, String> printTextFileMap = new LinkedHashMap<>();
		JSONObject demographicIdentity = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
		if (demographicIdentity == null)
			throw new IdentityNotFoundException(PlatformErrorMessages.PRT_PIS_IDENTITY_NOT_FOUND.getMessage());
		String printTextFileJson = utilities.getPrintTextFileJson(utilities.getConfigServerFileStorageURL(),
				utilities.getRegistrationProcessorPrintTextFile());
		JSONObject printTextFileJsonObject = JsonUtil.objectMapperReadValue(printTextFileJson, JSONObject.class);
		Set<String> printTextFileJsonKeys = printTextFileJsonObject.keySet();
		for (String key : printTextFileJsonKeys) {
			String printTextFileJsonString = JsonUtil.getJSONValue(printTextFileJsonObject, key);
			for (String value : printTextFileJsonString.split(",")) {
				Object object = demographicIdentity.get(value);
				if (object instanceof ArrayList) {
					JSONArray node = JsonUtil.getJSONArray(demographicIdentity, value);
					JsonValue[] jsonValues = JsonUtil.mapJsonNodeToJavaObject(JsonValue.class, node);
					for (JsonValue jsonValue : jsonValues) {
						/*
						 * if (jsonValue.getLanguage().equals(primaryLang)) printTextFileMap.put(value +
						 * "_" + primaryLang, jsonValue.getValue()); if
						 * (jsonValue.getLanguage().equals(secondaryLang)) printTextFileMap.put(value +
						 * "_" + secondaryLang, jsonValue.getValue());
						 */
						if (supportedLang.contains(jsonValue.getLanguage()))
							printTextFileMap.put(value + "_" + jsonValue.getLanguage(), jsonValue.getValue());

					}

				} else if (object instanceof LinkedHashMap) {
					JSONObject json = JsonUtil.getJSONObject(demographicIdentity, value);
					printTextFileMap.put(value, (String) json.get(VALUE));
				} else {
					printTextFileMap.put(value, (String) object);

				}
			}

		}

		Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
		String printTextFileString = gson.toJson(printTextFileMap);
		return printTextFileString.getBytes();
	}

	/**
	 * Sets the qr code.
	 *
	 * @param attributes   the attributes
	 * @return true, if successful
	 * @throws QrcodeGenerationException                          the qrcode
	 *                                                            generation
	 *                                                            exception
	 * @throws IOException                                        Signals that an
	 *                                                            I/O exception has
	 *                                                            occurred.
	 * @throws io.mosip.print.exception.QrcodeGenerationException
	 */
	private boolean setQrCode(String qrString, Map<String, Object> attributes,boolean isPhotoSet)
			throws QrcodeGenerationException, IOException, io.mosip.print.exception.QrcodeGenerationException {
		boolean isQRCodeSet = false;
		JSONObject qrJsonObj = JsonUtil.objectMapperReadValue(qrString, JSONObject.class);
		if(isPhotoSet) {
			qrJsonObj.remove("biometrics");
		}
		byte[] qrCodeBytes = qrCodeGenerator.generateQrCode(qrJsonObj.toString(), QrVersion.V30);
		if (qrCodeBytes != null) {
			String imageString = Base64.encodeBase64String(qrCodeBytes);
			attributes.put(QRCODE, "data:image/png;base64," + imageString);
			isQRCodeSet = true;
		}

		return isQRCodeSet;
	}
	/**
	 * Sets the Face qr code.
	 *
	 * @param attributes   the attributes
	 * @return true, if successful
	 * @throws QrcodeGenerationException                          the qrcode
	 *                                                            generation
	 *                                                            exception
	 * @throws IOException                                        Signals that an
	 *                                                            I/O exception has
	 *                                                            occurred.
	 * @throws io.mosip.print.exception.QrcodeGenerationException
	 */
	private boolean setFaceQrCode(String qrString, Map<String, Object> attributes,boolean isPhotoSet)
			throws QrcodeGenerationException, IOException, io.mosip.print.exception.QrcodeGenerationException {
		boolean isQRCodeSet = false;
		JSONObject qrJsonObj = JsonUtil.objectMapperReadValue(qrString, JSONObject.class);
		String applicantPhoto="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==";
		if(isPhotoSet) {
			qrJsonObj.remove("biometrics");
			qrJsonObj.put(APPLICANT_PHOTO,applicantPhoto);
		}
		byte[] qrCodeBytes = qrCodeGenerator.generateQrCode(qrJsonObj.toString(), QrVersion.V30);
		if (qrCodeBytes != null) {
			String imageString = Base64.encodeBase64String(qrCodeBytes);
			attributes.put(QRCODE, "data:image/png;base64," + imageString);
			isQRCodeSet = true;
		}

		return isQRCodeSet;
	}


	/**
	 * Sets the applicant photo.
	 *
	 *            the response
	 * @param attributes
	 *            the attributes
	 * @return true, if successful
	 * @throws Exception
	 *             the exception
	 */
	private boolean setApplicantPhoto(String individualBio, Map<String, Object> attributes) throws Exception {
		String value = individualBio;
		boolean isPhotoSet = false;

		if (value != null) {
			CbeffToBiometricUtil util = new CbeffToBiometricUtil(cbeffutil);
			List<String> subtype = new ArrayList<>();
			byte[] photoByte = util.getImageBytes(value, FACE, subtype);
			if (photoByte != null) {
				String data = java.util.Base64.getEncoder().encodeToString(extractFaceImageData(photoByte));
				attributes.put(APPLICANT_PHOTO, "data:image/png;base64," + data);
				isPhotoSet = true;
			}
		}
		return isPhotoSet;
	}

	/**
	 * Gets the artifacts.
	 *
	 * @param attribute    the attribute
	 * @return the artifacts
	 * @throws IOException    Signals that an I/O exception has occurred.
	 * @throws ParseException
	 */
	@SuppressWarnings("unchecked")
	private void setTemplateAttributes(String jsonString, Map<String, Object> attribute)
			throws IOException, ParseException {
		try {
			JSONObject demographicIdentity = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
			if (demographicIdentity == null)
				throw new IdentityNotFoundException(PlatformErrorMessages.PRT_PIS_IDENTITY_NOT_FOUND.getMessage());

			String mapperJsonString = utilities.getIdentityMappingJson(utilities.getConfigServerFileStorageURL(),
					utilities.getGetRegProcessorIdentityJson());
			JSONObject mapperJson = JsonUtil.objectMapperReadValue(mapperJsonString, JSONObject.class);
			JSONObject mapperIdentity = JsonUtil.getJSONObject(mapperJson,
					utilities.getGetRegProcessorDemographicIdentity());

			List<String> mapperJsonKeys = new ArrayList<>(mapperIdentity.keySet());
			for (String key : mapperJsonKeys) {
				LinkedHashMap<String, String> jsonObject = JsonUtil.getJSONValue(mapperIdentity, key);
				Object obj = null;
				String values = jsonObject.get(VALUE);
				for (String value : values.split(",")) {
					// Object object = demographicIdentity.get(value);
					Object object = demographicIdentity.get(value);
					if (object != null) {
						try {
							if (object instanceof Collection) {
								// In order to parse the collection values, mainly for VC.
								object = JsonUtil.writeValueAsString(object);
							}
							obj = new JSONParser().parse(object.toString());
						} catch (Exception e) {
							obj = object;
						}
					
						if (obj instanceof JSONArray) {
							// JSONArray node = JsonUtil.getJSONArray(demographicIdentity, value);
							JsonValue[] jsonValues = JsonUtil.mapJsonNodeToJavaObject(JsonValue.class, (JSONArray) obj);
							for (JsonValue jsonValue : jsonValues) {
								if (supportedLang.contains(jsonValue.getLanguage()))
									attribute.put(value + "_" + jsonValue.getLanguage(), jsonValue.getValue());
							}

						} else if (object instanceof JSONObject) {
							JSONObject json = (JSONObject) object;
							attribute.put(value, (String) json.get(VALUE));
						} else {
							attribute.put(value, String.valueOf(object));
						}
					}
				}
			}
		} catch (JsonParseException | JsonMappingException e) {
			printLogger.error("Error while parsing Json file" ,e);
			throw new ParsingException(PlatformErrorMessages.PRT_RGS_JSON_PARSING_EXCEPTION.getMessage(), e);
		}
	}

    /**
     * Gets the password.
     *
     * @param jsonObject
     * @return
     * @throws Exception
     */
    private String getPassword(org.json.JSONObject jsonObject) throws ApisResourceAccessException, IOException {
		String propertyValue = env.getProperty(UINCARDPASSWORD);
		if (Objects.isNull(propertyValue)) {
			throw new NullPointerException("Environment property '" + UINCARDPASSWORD + "' is missing.");
		}
        String[] attributes = propertyValue.split("\\|");
        List<String> list = new ArrayList<>(Arrays.asList(attributes));
        Iterator<String> it = list.iterator();
        String uinCardPd = "";
        Object obj = null;
        while (it.hasNext()) {
            String key = it.next().trim();
            Object object = jsonObject.get(key);
            if (object != null) {
                try {
                    obj = new JSONParser().parse(object.toString());
                } catch (Exception e) {
                    obj = object;
                }
                if (obj instanceof JSONArray) {
                    JsonValue[] jsonValues = JsonUtil.mapJsonNodeToJavaObject(JsonValue.class, (JSONArray) obj);
                    uinCardPd = uinCardPd.concat(getFormattedPasswordAttribute(getParameter(jsonValues, templateLang)));

                } else if (object instanceof org.json.simple.JSONObject) {
                    org.json.simple.JSONObject json = (org.json.simple.JSONObject) object;
                    uinCardPd = uinCardPd.concat(getFormattedPasswordAttribute((String) json.get(VALUE)));
                } else {
                    uinCardPd = uinCardPd.concat(getFormattedPasswordAttribute(object.toString()));
                }
            }
        }
        return uinCardPd;
    }

	private String getFormattedPasswordAttribute(String value) {
		String password = value.replaceAll("[^a-zA-Z0-9]+","");
		if (password.length() >= passwordLengthPerAttribute) {
			return password.substring(0, passwordLengthPerAttribute);
		} else {
			while (password.length() < passwordLengthPerAttribute) {
				password = password.repeat(2);
				if (password.length() >= passwordLengthPerAttribute) {
					password = password.substring(0, passwordLengthPerAttribute);
					break;
				}
			}
		}
		return password;
	}

	/**
	 * Gets the parameter.
	 *
	 * @param jsonValues
	 *            the json values
	 * @param langCode
	 *            the lang code
	 * @return the parameter
	 */
	private String getParameter(JsonValue[] jsonValues, String langCode) {

		String parameter = null;
		if (jsonValues != null) {
			for (int count = 0; count < jsonValues.length; count++) {
				String lang = jsonValues[count].getLanguage();
				if (langCode.contains(lang)) {
					parameter = jsonValues[count].getValue();
					break;
				}
			}
		}
		return parameter;
	}

	public byte[] extractFaceImageData(byte[] decodedBioValue) {

		try (DataInputStream din = new DataInputStream(new ByteArrayInputStream(decodedBioValue))) {

			byte[] format = new byte[4];
			din.read(format, 0, 4);
			byte[] version = new byte[4];
			din.read(version, 0, 4);
			int recordLength = din.readInt();
			short numberofRepresentionRecord = din.readShort();
			byte certificationFlag = din.readByte();
			byte[] temporalSequence = new byte[2];
			din.read(temporalSequence, 0, 2);
			int representationLength = din.readInt();
			byte[] representationData = new byte[representationLength - 4];
			din.read(representationData, 0, representationData.length);
			try (DataInputStream rdin = new DataInputStream(new ByteArrayInputStream(representationData))) {
				byte[] captureDetails = new byte[14];
				rdin.read(captureDetails, 0, 14);
				byte noOfQualityBlocks = rdin.readByte();
				if (noOfQualityBlocks > 0) {
					byte[] qualityBlocks = new byte[noOfQualityBlocks * 5];
					rdin.read(qualityBlocks, 0, qualityBlocks.length);
				}
				short noOfLandmarkPoints = rdin.readShort();
				byte[] facialInformation = new byte[15];
				rdin.read(facialInformation, 0, 15);
				if (noOfLandmarkPoints > 0) {
					byte[] landmarkPoints = new byte[noOfLandmarkPoints * 8];
					rdin.read(landmarkPoints, 0, landmarkPoints.length);
				}
				byte faceType = rdin.readByte();
				byte imageDataType = rdin.readByte();
				byte[] otherImageInformation = new byte[9];
				rdin.read(otherImageInformation, 0, otherImageInformation.length);
				int lengthOfImageData = rdin.readInt();

				byte[] image = new byte[lengthOfImageData];
				rdin.read(image, 0, lengthOfImageData);

				return image;
			}
		} catch (Exception ex) {
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					ex.getMessage() + ExceptionUtils.getStackTrace(ex));
		}
	}

    private String getCrdentialSubject(String crdential) {
        org.json.JSONObject jsonObject = new org.json.JSONObject(crdential);
        return jsonObject.get("credentialSubject").toString();
    }

    private void printStatusUpdate(String requestId, String status, String datashareUrl) {

        CredentialStatusEvent creEvent = new CredentialStatusEvent();
        LocalDateTime currentDtime = DateUtils.getUTCCurrentDateTime();
        StatusEvent sEvent = new StatusEvent();
        sEvent.setId(UUID.randomUUID().toString());
        sEvent.setRequestId(requestId);
        sEvent.setStatus(status);
        if (datashareUrl != null) {
            sEvent.setUrl(datashareUrl);
        }
        sEvent.setTimestamp(Timestamp.valueOf(currentDtime).toString());
        creEvent.setPublishedOn(new DateTime().toString());
        creEvent.setPublisher("PRINT_SERVICE");
        creEvent.setTopic(topic);
        creEvent.setEvent(sEvent);
        webSubSubscriptionHelper.printStatusUpdateEvent(topic, creEvent);
    }

	public org.json.JSONObject decryptAttribute(org.json.JSONObject data, String encryptionPin, String credential)
			throws ParseException {

		// org.json.JSONObject jsonObj = new org.json.JSONObject(credential);
		JSONParser parser = new JSONParser(); // this needs the "json-simple" library
		Object obj = parser.parse(credential);
		JSONObject jsonObj = (org.json.simple.JSONObject) obj;

		JSONArray jsonArray = (JSONArray) jsonObj.get("protectedAttributes");
		if (Objects.isNull(jsonArray)) {
			return data;
		}
		for (Object str : jsonArray) {

				CryptoWithPinRequestDto cryptoWithPinRequestDto = new CryptoWithPinRequestDto();
				CryptoWithPinResponseDto cryptoWithPinResponseDto = new CryptoWithPinResponseDto();

				cryptoWithPinRequestDto.setUserPin(encryptionPin);
				cryptoWithPinRequestDto.setData(data.getString(str.toString()));
				try {
					cryptoWithPinResponseDto = cryptoUtil.decryptWithPin(cryptoWithPinRequestDto);
				} catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException
						| InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
					printLogger.error("Error while decrypting the data" ,e);
					throw new CryptoManagerException(PlatformErrorMessages.PRT_INVALID_KEY_EXCEPTION.getCode(),
							PlatformErrorMessages.PRT_INVALID_KEY_EXCEPTION.getMessage(), e);
				}
				data.put((String) str, cryptoWithPinResponseDto.getData());
			
			}

		return data;
	}
}