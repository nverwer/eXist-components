package com.rakensi;

import static com.rakensi.SVGQRModule.functionSignature;
import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returns;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.LineMetrics;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.jfree.graphics2d.svg.MeetOrSlice;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.jfree.graphics2d.svg.ViewBox;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * A class for making QR codes in SVG.
 *
 * Created using
 * - https://medium.com/@thieunguyenhung/how-to-generate-qr-code-in-svg-format-9c951bf2ed21
 * - https://zxing.github.io/zxing/apidocs/com/google/zxing/common/BitMatrix.html
 * - https://www.jfree.org/jfreesvg/javadoc/org.jfree.svg/org/jfree/svg/SVGGraphics2D.html
 * - https://github.com/eXist-db/exist-apps-archetype
 * - https://www.eclipse.org/m2e/documentation/m2e-execution-not-covered.html#execute-plugin-goal
 */
public class SVGQRFunctions extends BasicFunction
{

  private static final String FS_GENERATE_NAME = "generate-qr-svg";
  static final FunctionSignature FS_GENERATE = functionSignature(
    FS_GENERATE_NAME,
    "Create a QR code as SVG for some text (e.g., a URL).",
    returns(Type.DOCUMENT),
    optParam("qr-text", Type.STRING, "A text that is converted to a QR code")
  );

  private static final String FS_GENERATE_TEXT_NAME = "generate-qr-text-svg";
  static final FunctionSignature FS_GENERATE_TEXT = functionSignature(
    FS_GENERATE_TEXT_NAME,
    "Create a QR code as SVG for some text (e.g., a URL) and put text below it.",
    returns(Type.DOCUMENT),
    optParam("qr-text", Type.STRING, "A text that is converted to a QR code"),
    param("caption", Type.STRING, "A text that is placed below the QR code")  );

  public SVGQRFunctions(final XQueryContext context, final FunctionSignature signature)
  {
    super(context, signature);
  }

  @Override
  public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException
  {
    switch (getName().getLocalPart())
    {
    case FS_GENERATE_NAME:
      final Optional<StringValue> qrCodeText = args[0].isEmpty() ? Optional.empty() : Optional.of((StringValue) args[0].itemAt(0));
      return generateQrSvg(qrCodeText);
    case FS_GENERATE_TEXT_NAME:
      return generateQrTextSvg(
          args[0].isEmpty() ? Optional.empty() : Optional.of((StringValue) args[0].itemAt(0)),
          args[1].isEmpty() ? Optional.empty() : Optional.of((StringValue) args[1].itemAt(0))
        );
    default:
      throw new XPathException(ErrorCodes.XPST0017,
          "No function defined in SVGQRFunctions for " + getName() + "#" + getSignature().getArgumentCount());
    }
  }

  public DocumentImpl generateQrSvg(final Optional<StringValue> qrCodeText) throws XPathException
  {
    return generateQrTextSvg(qrCodeText, Optional.empty());
  }

  public DocumentImpl generateQrTextSvg(final Optional<StringValue> qrCodeText, Optional<StringValue> userText)
      throws XPathException
  {
    String qrCodeTextString = qrCodeText.map(StringValue::toString).orElse("");
    String userTextString = userText.map(StringValue::toString).orElse("");
    String svgElement = generateQrSvgElement(qrCodeTextString, userTextString);
    try
    {
      DocumentImpl document = parseXmlElementString(svgElement);
      return document;
    }
    catch (final Exception e)
    {
      throw new XPathException(ErrorCodes.ERROR, e.getMessage(), e);
    }
  }

  /**
   * Helper function to parse an XML string using a MemTreeBuilder.
   *
   * @param xml
   * @return
   * @throws SAXException
   * @throws ParserConfigurationException
   * @throws IOException
   */
  private DocumentImpl parseXmlElementString(String xml) throws SAXException, ParserConfigurationException, IOException
  {
    final MemTreeBuilder builder = new MemTreeBuilder();
    DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    XMLReader reader = factory.newSAXParser().getXMLReader();
    reader.setContentHandler(receiver);
    builder.startDocument();
    reader.parse(new InputSource(new StringReader(xml)));
    builder.endDocument();
    DocumentImpl document = builder.getDocument();
    return document;
  }


  /* The actual code is below. */

  private static int DEFAULT_MARGIN = 0;
  private static int DEFAULT_SIZE = 1;
  private static int pixelsPerBlock = 10;

  private static String generateQrSvgElement(String qrCodeText, String userText) throws XPathException
  {
    if (qrCodeText == null) qrCodeText = "";
    if (userText == null) userText = "";

    Map<EncodeHintType, Object> encodeHints = new HashMap<>();
    encodeHints.put(EncodeHintType.MARGIN, DEFAULT_MARGIN);
    encodeHints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);

    BitMatrix bitMatrix = null;
    try
    {
      QRCodeWriter qrCodeWriter = new QRCodeWriter();
      bitMatrix = qrCodeWriter.encode(qrCodeText, BarcodeFormat.QR_CODE, DEFAULT_SIZE, DEFAULT_SIZE, encodeHints);
    }
    catch (final WriterException e)
    {
      throw new XPathException(ErrorCodes.ERROR, e.getMessage(), e);
    }

    int[] ltwh = bitMatrix.getEnclosingRectangle(); // left, top, width, height
    int matrixLeft = ltwh[0];
    int matrixTop = ltwh[1];
    int matrixWidth = ltwh[2];
    int matrixHeight = ltwh[3];
    // int matrixRight = matrixLeft + matrixWidth;
    // int matrixBottom = matrixTop + matrixHeight;

    int svgWidth = pixelsPerBlock * matrixWidth;
    int svgHeight = pixelsPerBlock * matrixHeight;
    SVGGraphics2D g2 = new SVGGraphics2D(svgWidth, svgHeight);
    g2.setColor(Color.BLACK);

    // If there is userText, adjust the canvas size and draw the text.
    if (userText.length() > 0)
    {
      // https://stackoverflow.com/a/27740330/1021892
      // Shrink the font size until the text width fits.
      Font font = null;
      LineMetrics lineMetrics = null;
      // These starting values should lead to fast convergence to the right fontSize.
      double fontSize = 256;
      int textWidth = 2 * svgWidth;
      int textHeight = 0;
      while (textWidth > svgWidth)
      {
        fontSize = fontSize * svgWidth / textWidth;
        font = new Font(Font.MONOSPACED, Font.BOLD, (int) fontSize);
        lineMetrics = font.getLineMetrics(userText, g2.getFontRenderContext());
        textHeight = (int) lineMetrics.getHeight();
        textWidth = g2.getFontMetrics(font).stringWidth(userText);
      }
      // Adjust the SVG canvas size.
      int svgWithTextHeight = svgHeight + textHeight;
      int x = (svgWidth - textWidth) / 2;
      int y = svgWithTextHeight - (int) (lineMetrics.getDescent() + lineMetrics.getLeading());
      g2 = new SVGGraphics2D(svgWidth, svgWithTextHeight);
      // Plot the text
      g2.setColor(Color.BLACK);
      g2.setFont(font);
      g2.drawString(userText, x, y);
    }

    // Now plot the QR code.
    for (int i = 0; i < matrixWidth; i++)
    {
      for (int j = 0; j < matrixHeight; j++)
      {
        if (bitMatrix.get(matrixLeft + i, matrixTop + j))
        {
          g2.fillRect(pixelsPerBlock * i, pixelsPerBlock * j, pixelsPerBlock, pixelsPerBlock);
        }
      }
    }

    // Generate the SVG element.
    ViewBox viewBox = new ViewBox(0, 0, g2.getWidth(), g2.getHeight());
    String svgElement = g2.getSVGElement(null, false, viewBox, null, MeetOrSlice.MEET);
    return svgElement;
  }

}
