package commons.poi.word.entity;
import java.io.InputStream;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlToken;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扩充document,修复图片插入失败问题问题
 *
 * @author JEECG
 * @date 2013-11-20
 * @version 1.0
 */
public class MyXWPFDocument extends XWPFDocument {

	private static final Logger LOGGER = LoggerFactory.getLogger(MyXWPFDocument.class);

	private static String PICXML = "" + "<query:graphic xmlns:query=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" + "   <query:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" + "      <pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" + "         <pic:nvPicPr>" + "            <pic:cNvPr id=\"%s\" name=\"Generated\"/>" + "            <pic:cNvPicPr/>" + "         </pic:nvPicPr>" + "         <pic:blipFill>" + "            <query:blip r:embed=\"%s\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>" + "            <query:stretch>" + "               <query:fillRect/>" + "            </query:stretch>" + "         </pic:blipFill>" + "         <pic:spPr>" + "            <query:xfrm>"
			+ "               <query:off x=\"0\" y=\"0\"/>" + "               <query:ext cx=\"%s\" cy=\"%s\"/>" + "            </query:xfrm>" + "            <query:prstGeom prst=\"rect\">" + "               <query:avLst/>" + "            </query:prstGeom>" + "         </pic:spPr>" + "      </pic:pic>" + "   </query:graphicData>" + "</query:graphic>";

	public MyXWPFDocument() {
		super();
	}

	public MyXWPFDocument(InputStream in) throws Exception {
		super(in);
	}

	public MyXWPFDocument(OPCPackage opcPackage) throws Exception {
		super(opcPackage);
	}

	public void createPicture(String blipId, int id, int width, int height) {
		final int EMU = 9525;
		width *= EMU;
		height *= EMU;
		CTInline inline = createParagraph().createRun().getCTR().addNewDrawing().addNewInline();
		String picXml = String.format(PICXML, id, blipId, width, height);
		XmlToken xmlToken = null;
		try {
			xmlToken = XmlToken.Factory.parse(picXml);
		} catch (XmlException xe) {
			LOGGER.error(xe.getMessage(), xe.fillInStackTrace());
		}
		inline.set(xmlToken);

		inline.setDistT(0);
		inline.setDistB(0);
		inline.setDistL(0);
		inline.setDistR(0);

		CTPositiveSize2D extent = inline.addNewExtent();
		extent.setCx(width);
		extent.setCy(height);

		CTNonVisualDrawingProps docPr = inline.addNewDocPr();
		docPr.setId(id);
		docPr.setName("Picture " + id);
		docPr.setDescr("Generated");
	}

	public void createPicture(XWPFRun run, String blipId, int id, int width, int height) {
		final int EMU = 9525;
		width *= EMU;
		height *= EMU;
		CTInline inline = run.getCTR().addNewDrawing().addNewInline();
		String picXml = String.format(PICXML, id, blipId, width, height);
		XmlToken xmlToken = null;
		try {
			xmlToken = XmlToken.Factory.parse(picXml);
		} catch (XmlException xe) {
			LOGGER.error(xe.getMessage(), xe.fillInStackTrace());
		}
		inline.set(xmlToken);

		inline.setDistT(0);
		inline.setDistB(0);
		inline.setDistL(0);
		inline.setDistR(0);

		CTPositiveSize2D extent = inline.addNewExtent();
		extent.setCx(width);
		extent.setCy(height);

		CTNonVisualDrawingProps docPr = inline.addNewDocPr();
		docPr.setId(id);
		docPr.setName("Picture " + id);
		docPr.setDescr("Generated");
	}

}
