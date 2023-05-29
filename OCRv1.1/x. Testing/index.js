const { execSync } = require('child_process');

exports.processImage = async (event, context) => {
  try {
    const { bucket, name } = event;

    // Build the command to trigger the Java OCR code
    const command = `java -jar target/functions-ocr-process-image.jar gs://${bucket}/${name}`;

    // Execute the command synchronously
    const output = execSync(command).toString();

    console.log('OCR extraction completed:', output);

    return {
      statusCode: 200,
      body: 'OCR extraction completed',
    };
  } catch (error) {
    console.error('OCR extraction failed:', error);

    return {
      statusCode: 500,
      body: 'OCR extraction failed',
    };
  }
};
