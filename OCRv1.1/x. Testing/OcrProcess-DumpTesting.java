import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

public class OCRToGoogleSheet {
    private static final String APPLICATION_NAME = "OCRToGoogleSheet";
    private static final String SPREADSHEET_BASE_URL = "https://docs.google.com/spreadsheets/d/";
    private static final String SPREADSHEET_API_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final String SPREADSHEET_RANGE = "Sheet1!A1";

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        // Replace with your Google Cloud Storage bucket and file name
        String bucketName = "gs://wot-uploads2";
        String fileName = "gs://wot-uploads2/FaZe Clan GC-Scoreboard (Fracture -052023).png";

        // Download the image from Google Cloud Storage
        ByteString imgBytes = downloadImageFromBucket(bucketName, fileName);

        // Perform OCR text extraction
        String extractedText = performTextExtraction(imgBytes);

        // Check if text was detected
        if (extractedText.isEmpty()) {
            System.out.println("No Text Detected.");
            return;
        }

        // Store the extracted text in a Google Sheet
        String sheetName = fileName.replaceFirst("[.][^.]+$", "");
        storeTextInGoogleSheet(sheetName, extractedText);
    }

    private static ByteString downloadImageFromBucket(String bucketName, String fileName) throws IOException {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        Blob blob = storage.get(bucketName, fileName);
        return ByteString.copyFrom(blob.getContent());
    }

    private static String performTextExtraction(ByteString imgBytes) throws IOException {
        try (com.google.cloud.vision.v1.ImageAnnotatorClient visionClient = com.google.cloud.vision.v1.ImageAnnotatorClient.create()) {
            // Build the image request
            com.google.cloud.vision.v1.Image image = com.google.cloud.vision.v1.Image.newBuilder().setContent(imgBytes).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(image).build();
            List<AnnotateImageRequest> requests = Collections.singletonList(request);

            // Perform the text detection
            BatchAnnotateImagesResponse response = visionClient.batchAnnotateImages(requests);
            List<AnnotateImageResponse> imageResponses = response.getResponsesList();

            // Process the response
            for (AnnotateImageResponse imageResponse : imageResponses) {
                if (imageResponse.hasError()) {
                    System.err.println("Error: " + imageResponse.getError().getMessage());
                    return "";
                }

                // Extract the text
                TextAnnotation textAnnotation = imageResponse.getFullTextAnnotation();
                if (textAnnotation != null) {
                    return textAnnotation.getText();
                }
            }
        }
        return "";
    }

    private static void storeTextInGoogleSheet(String sheetName, String extractedText) throws IOException, GeneralSecurityException {
        Credential credential = GoogleCredential.getApplicationDefault().createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        // Create a Sheets service object
        Sheets sheetsService = new Sheets.Builder(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(), com.google.api.client.json.gson.GsonFactory.getDefaultInstance(), credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Create a new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setProperties(new SpreadsheetProperties().setTitle(sheetName));
        spreadsheet = sheetsService.spreadsheets().create(spreadsheet).execute();
        String spreadsheetId = spreadsheet.getSpreadsheetId();
        String spreadsheetUrl = SPREADSHEET_BASE_URL + spreadsheetId;

        // Write the extracted text to the spreadsheet
        List<List<Object>> values = Arrays.asList(Arrays.asList(extractedText));
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values().update(spreadsheetId, SPREADSHEET_RANGE, body)
                .setValueInputOption("RAW")
                .execute();

        System.out.println("Extracted text is stored in the Google Sheet: " + spreadsheetUrl);
    }
}
