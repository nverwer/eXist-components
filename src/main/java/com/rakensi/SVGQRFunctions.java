package com.rakensi;

import static com.rakensi.SVGQRModule.functionSignature;
import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.returns;

import java.awt.Color;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.memtree.DocumentBuilderReceiver;
//import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
//import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.jfree.graphics2d.svg.SVGGraphics2D;
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
 * A function to make QR codes in SVG.
 *
 * Created using
 * - https://github.com/eXist-db/exist-apps-archetype
 * - https://www.eclipse.org/m2e/documentation/m2e-execution-not-covered.html#execute-plugin-goal
 */
public class SVGQRFunctions extends BasicFunction {

/*
    private static final String FS_HELLO_WORLD_NAME = "hello-world";
    static final FunctionSignature FS_HELLO_WORLD = functionSignature(
        FS_HELLO_WORLD_NAME,
        "An example function that returns <hello>world</hello>.",
        returns(Type.DOCUMENT),
        null
    );

    private static final String FS_SAY_HELLO_NAME = "say-hello";
    static final FunctionSignature FS_SAY_HELLO = functionSignature(
            FS_SAY_HELLO_NAME,
            "An example function that returns <hello>{$name}</hello>.",
            returns(Type.DOCUMENT),
            optParam("name", Type.STRING, "A name")
    );

    private static final String FS_ADD_NAME = "add";
    static final FunctionSignature FS_ADD = functionSignature(
            FS_ADD_NAME,
            "An example function that adds two numbers together.",
            returns(Type.INT),
            param("a", Type.INT, "A number"),
            param("b", Type.INT, "A number")
    );
*/

  private static final String FS_CREATE_FOR_NAME = "create-for";
  static final FunctionSignature FS_CREATE_FOR = functionSignature(
          FS_CREATE_FOR_NAME,
          "A function to create a QR code for some text (e.g., a URL) as SVG.",
          returns(Type.ELEMENT),
          optParam("qrText", Type.STRING, "A text to convvert to a QR code")
  );


    public SVGQRFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        switch (getName().getLocalPart()) {

            case FS_CREATE_FOR_NAME:
                return createFor(Optional.of(new StringValue("")));
/*
            case FS_SAY_HELLO_NAME:
                final Optional<StringValue> name = args[0].isEmpty() ? Optional.empty() : Optional.of((StringValue)args[0].itemAt(0));
                return sayHello(name);

            case FS_ADD_NAME:
                final IntegerValue a = (IntegerValue) args[0].itemAt(0);
                final IntegerValue b = (IntegerValue) args[1].itemAt(0);
                return add(a, b);
*/
            default:
                throw new XPathException(ErrorCodes.XPST0017, "No function: " + getName() + "#" + getSignature().getArgumentCount());
        }
    }

    private DocumentImpl createFor(final Optional<StringValue> qrText) throws XPathException {
        try {
            final MemTreeBuilder builder = new MemTreeBuilder(context);
            parseString(builder, createQrSvg(qrText.map(StringValue::toString).orElse("")));
            /*
            builder.startDocument();
            builder.startElement(new QName("hello"), null);
            builder.characters(name.map(StringValue::toString).orElse("stranger"));
            builder.endElement();
            builder.endDocument();
            */
            return builder.getDocument();
        } catch (final Exception e) {
            throw new XPathException(ErrorCodes.ERROR, e.getMessage(), e);
        }
    }

    private static final int DEFAULT_MARGIN = 4;
    private static final int DEFAULT_SIZE = 500;

    private String createQrSvg(String qrCodeText) throws WriterException {
      Map<EncodeHintType, Object> hints = new HashMap<>();
      hints.put(EncodeHintType.MARGIN, DEFAULT_MARGIN);
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
      BitMatrix bitMatrix = new QRCodeWriter().encode(qrCodeText, BarcodeFormat.QR_CODE, DEFAULT_SIZE, DEFAULT_SIZE, hints);

      int matrixWidth = bitMatrix.getWidth();
      int matrixHeight = bitMatrix.getHeight();
      SVGGraphics2D g2 = new SVGGraphics2D(matrixWidth, matrixWidth);
      g2.setColor(Color.BLACK);

      for (int i = 0; i < matrixWidth; i++) {
        for (int j = 0; j < matrixHeight; j++) {
          if (bitMatrix.get(i, j)) {
            g2.fillRect(i, j, 1, 1);
          }
        }
      }

      return g2.getSVGElement();
    }

    private void parseString(MemTreeBuilder builder, String xml) throws SAXException, ParserConfigurationException, IOException {
      DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(true);
      XMLReader reader = factory.newSAXParser().getXMLReader();
      reader.setContentHandler(receiver);
      reader.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Adds two numbers together.
     *
     * @param a The first number
     * @param b The second number
     *
     * @return The result;
    private IntegerValue add(final IntegerValue a, final IntegerValue b) throws XPathException {
        final int result = a.getInt() + b.getInt();
        return new IntegerValue(result);
    }
     */
}
