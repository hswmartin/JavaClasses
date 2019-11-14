package com.genexus.db.driver;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.HttpMethod;
import com.ibm.cloud.objectstorage.SDKGlobalConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3Client;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.model.*;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import java.sql.Timestamp;
import java.util.List;

import com.genexus.Application;
import com.genexus.StructSdtMessages_Message;
import com.genexus.util.Encryption;
import com.genexus.util.GXService;
import com.genexus.util.StorageUtils;
import com.ibm.cloud.objectstorage.util.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;


public class ExternalProviderIBM implements ExternalProvider {

	private static Logger logger = LogManager.getLogger(ExternalProviderIBM.class);

    static final String ACCESS_API_KEY = "STORAGE_PROVIDER_API_KEY";
    static final String SERVICE_COS_INSTANCE_ID = "STORAGE_PROVIDER_SERVICE_INSTANCE_ID";
    static final String COS_ENDPOINT = "ENDPOINT";
    static final String BUCKET = "BUCKET_NAME";
    static final String FOLDER = "FOLDER_NAME";

    private AmazonS3 client;
    private String bucket;
    private String folder;
    private String endpointUrl = ""; //"s3.sao01.cloud-object-storage.appdomain.cloud/";

    public ExternalProviderIBM(String service) {

        GXService providerService = Application.getGXServices().get(service);

        String api_key = ExternalProviderHelper.getServicePropertyValue(providerService, ACCESS_API_KEY, true);
		String service_instance_id = ExternalProviderHelper.getServicePropertyValue(providerService, SERVICE_COS_INSTANCE_ID, true);
		String location = ExternalProviderHelper.getServicePropertyValue(providerService, "STORAGE_COS_LOCATION", false);
		endpointUrl = ExternalProviderHelper.getServicePropertyValue(providerService, COS_ENDPOINT, false);
		String bucket = ExternalProviderHelper.getServicePropertyValue(providerService, BUCKET, false);
		String folder = ExternalProviderHelper.getServicePropertyValue(providerService, FOLDER, false);

		init(api_key, service_instance_id, bucket, folder, location);
    }


	public ExternalProviderIBM(String api_key, String service_instance_id,  String bucketName, String folderName, String location) {
		init(api_key, service_instance_id, bucketName, folderName, location);
	}

	private void init(String api_key, String service_instance_id, String bucketName, String folderName, String location) {
		ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(5000);
		clientConfig.setUseTcpKeepAlive(true);
		SDKGlobalConfiguration.IAM_ENDPOINT = "https://iam.cloud.ibm.com/identity/token";
		AWSCredentials credentials = new BasicIBMOAuthCredentials(api_key, service_instance_id);

		client = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
			.withEndpointConfiguration(new EndpointConfiguration(endpointUrl, location)).withPathStyleAccessEnabled(true)
			.withClientConfiguration(clientConfig).build();

		bucket = bucketName;
		folder = folderName;

		bucketExists();
		createFolder(folder);
	}


	private void bucketExists() {
        if (!client.doesBucketExist(bucket)) {
        	logger.debug(String.format("Bucket %s doesn't exist, please create the bucket", bucket));
        }
    }

    private void createFolder(String folderName) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(0);
        InputStream emptyContent = new ByteArrayInputStream(new byte[0]);
        folderName = StorageUtils.normalizeDirectoryName(folderName);
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, folderName, emptyContent, metadata);
        client.putObject(putObjectRequest);
    }

    public void download(String externalFileName, String localFile, boolean isPrivate) {
        OutputStream outputStream = null;
        try {
            S3Object object = client.getObject(new GetObjectRequest(bucket, externalFileName));
            InputStream objectData = object.getObjectContent();
            outputStream = new FileOutputStream(new File(localFile));
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = objectData.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
            objectData.close();
            outputStream.close();
        } catch (FileNotFoundException ex) {
        	logger.error("Error while downloading file to the external provider", ex);
        } catch (IOException ex) {
        	logger.error("Error while downloading file to the external provider", ex);
        }
    }

    public String upload(String localFile, String externalFileName, boolean isPrivate) {
        PutObjectResult result = client.putObject(new PutObjectRequest(bucket, externalFileName, new File(localFile)).withCannedAcl(getUploadACL(isPrivate)));
        return ((AmazonS3Client) client).getResourceUrl(bucket, externalFileName);
    }

	private CannedAccessControlList getUploadACL(boolean isPrivate) {
		CannedAccessControlList accessControl = CannedAccessControlList.PublicRead;
        if (isPrivate) {
            accessControl = CannedAccessControlList.Private;
        }
		return accessControl;
	}

    public String upload(String externalFileName, InputStream input, boolean isPrivate) {
        byte[] bytes;
        try {
            bytes = IOUtils.toByteArray(input);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            if (externalFileName.endsWith(".tmp")) {
                metadata.setContentType("image/jpeg");
            }
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            PutObjectResult result = client.putObject(new PutObjectRequest(bucket, externalFileName, byteArrayInputStream, metadata).withCannedAcl(getUploadACL(isPrivate)));
            return ((AmazonS3Client) client).getResourceUrl(bucket, externalFileName);
        } catch (IOException ex) {
            logger.error("Error while uploading file to the external provider.", ex);
            return "";
        }
    }

    public String get(String externalFileName, boolean isPrivate, int expirationMinutes) {
        client.getObjectMetadata(bucket, externalFileName);
        if (isPrivate) {
            Date expiration = new Date();
            long msec = expiration.getTime();
            msec += 60000 * expirationMinutes;
            expiration.setTime(msec);

            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucket, externalFileName);
            generatePresignedUrlRequest.setMethod(HttpMethod.GET);
            generatePresignedUrlRequest.setExpiration(expiration);
            return client.generatePresignedUrl(generatePresignedUrlRequest).toString();
        } else {
            return ((AmazonS3Client) client).getResourceUrl(bucket, externalFileName);
        }
    }

    public void delete(String objectName, boolean isPrivate) {
        client.deleteObject(bucket, objectName);
    }

    public String rename(String objectName, String newName, boolean isPrivate) {
        String url = copy(objectName, newName, isPrivate);
        delete(objectName, isPrivate);
        return url;
    }

    public String copy(String objectName, String newName, boolean isPrivate) {
        CopyObjectRequest request = new CopyObjectRequest(bucket, objectName, bucket, newName);
        request.setCannedAccessControlList(CannedAccessControlList.PublicReadWrite);
        client.copyObject(request);
        return ((AmazonS3Client) client).getResourceUrl(bucket, newName);
    }

    public String copy(String objectUrl, String newName, String tableName, String fieldName, boolean isPrivate) {
        String resourceFolderName = folder + StorageUtils.DELIMITER + tableName + StorageUtils.DELIMITER + fieldName;
        createFolder(resourceFolderName);

        String resourceKey = resourceFolderName + StorageUtils.DELIMITER + newName;
        objectUrl = objectUrl.replace("https://" + bucket + endpointUrl, "");

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata("Table", tableName);
        metadata.addUserMetadata("Field", fieldName);
        metadata.addUserMetadata("KeyValue", resourceKey);

        CopyObjectRequest request = new CopyObjectRequest(bucket, objectUrl, bucket, resourceKey);
        request.setNewObjectMetadata(metadata);
        request.setCannedAccessControlList(CannedAccessControlList.PublicReadWrite);
        client.copyObject(request);

        return ((AmazonS3Client) client).getResourceUrl(bucket, resourceKey);
    }

    public long getLength(String objectName, boolean isPrivate) {
        ObjectMetadata obj = client.getObjectMetadata(bucket, objectName);
        return obj.getInstanceLength();
    }

    public Date getLastModified(String objectName, boolean isPrivate) {
        ObjectMetadata obj = client.getObjectMetadata(bucket, objectName);
        return obj.getLastModified();
    }

    public boolean exists(String objectName, boolean isPrivate) {
        try {
            client.getObjectMetadata(bucket, objectName);
        } catch (AmazonS3Exception ex) {
            if (ex.getStatusCode() == 404) {
                return false;
            }
        }
        return true;
    }

    public String getDirectory(String directoryName) {
        if (existsDirectory(directoryName)) {
            return bucket + ":" + StorageUtils.DELIMITER + directoryName + StorageUtils.DELIMITER;
        } else {
            return "";
        }
    }

    public boolean existsDirectory(String directoryName) {
        directoryName = StorageUtils.normalizeDirectoryName(directoryName);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket).withDelimiter(StorageUtils.DELIMITER);
        List<String> directories = new ArrayList<String>();
        for (String prefix : client.listObjects(listObjectsRequest).getCommonPrefixes()) {
            directories.add(prefix);
            getAllDirectories(prefix, directories);
        }
        return directories.contains(directoryName);
    }

    public void getAllDirectories(String directoryName, List<String> directories) {
        directoryName = StorageUtils.normalizeDirectoryName(directoryName);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket).withPrefix(directoryName).withDelimiter(StorageUtils.DELIMITER);
        for (String prefix : client.listObjects(listObjectsRequest).getCommonPrefixes()) {
            directories.add(prefix);
            getAllDirectories(prefix, directories);
        }
    }

    public void createDirectory(String directoryName) {
        createFolder(directoryName);
    }

    public void deleteDirectory(String directoryName) {
        for (S3ObjectSummary file : client.listObjects(bucket, directoryName).getObjectSummaries()) {
            client.deleteObject(bucket, file.getKey());
        }
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket).withDelimiter(StorageUtils.DELIMITER);
        ObjectListing list = client.listObjects(listObjectsRequest);
        List<String> toRemove = new ArrayList<String>();
        List<String> prefixes = list.getCommonPrefixes();
        for (String prefix : prefixes) {
            if (prefix.startsWith(directoryName)) {
                toRemove.add(prefix);
            }
        }
        prefixes.removeAll(toRemove);
        list.setCommonPrefixes(prefixes);
    }

    public void renameDirectory(String directoryName, String newDirectoryName) {
        directoryName = StorageUtils.normalizeDirectoryName(directoryName);
        newDirectoryName = StorageUtils.normalizeDirectoryName(newDirectoryName);
        createFolder(newDirectoryName);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket).withPrefix(directoryName);
        for (S3ObjectSummary file : client.listObjects(listObjectsRequest).getObjectSummaries()) {
            String newKey = file.getKey().replace(directoryName, newDirectoryName);
            rename(file.getKey(), newKey, false);
        }
        deleteDirectory(directoryName);
    }

    public List<String> getFiles(String directoryName, String filter) {
    	filter = (filter == null || filter.isEmpty())? null: filter.replace("*", "");
        List<String> files = new ArrayList<String>();
        directoryName = StorageUtils.normalizeDirectoryName(directoryName);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket).withPrefix(directoryName).withDelimiter(StorageUtils.DELIMITER);   
        for (S3ObjectSummary file : client.listObjects(listObjectsRequest).getObjectSummaries()) {        	
        	String key = file.getKey();        	
            if (isFile(directoryName, key) && (filter == null || filter.isEmpty() || key.contains(filter))) {
                files.add(key);
            }
        }
        return files;
    }

    private boolean isFile(String directory, String name) {        
        return !name.endsWith(StorageUtils.DELIMITER);        
    }

    public List<String> getFiles(String directoryName) {
        return getFiles(directoryName, null);
    }

    public List<String> getSubDirectories(String directoryName) {
        directoryName = StorageUtils.normalizeDirectoryName(directoryName);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucket).withPrefix(directoryName)
                .withDelimiter(StorageUtils.DELIMITER);
        ObjectListing objects = client.listObjects(listObjectsRequest);
        return objects.getCommonPrefixes();
    }

    public InputStream getStream(String objectName, boolean isPrivate) {
        OutputStream out = new ByteArrayOutputStream();

        S3Object object = client.getObject(new GetObjectRequest(bucket, objectName));
        return object.getObjectContent();
    }

    public boolean getMessageFromException(Exception ex, StructSdtMessages_Message msg) {
        try {
            AmazonS3Exception aex = (AmazonS3Exception) ex;
            msg.setId(aex.getErrorCode());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
