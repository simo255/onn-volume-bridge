package io.github.simo255.onnvolumebridge;
import java.io.StringReader;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.w3c.dom.*;
final class XmlValues {
    static Document parse(String text) throws Exception {
        String upper=text.toUpperCase(Locale.ROOT);
        if(upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY"))throw new SAXException("DTD and entities are not allowed");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder b=f.newDocumentBuilder();
        b.setEntityResolver((publicId,systemId)->{throw new SAXException("External entities are not allowed");});
        return b.parse(new InputSource(new StringReader(text)));
    }
    static String value(Document doc,String name) {
        NodeList nodes=doc.getElementsByTagNameNS("*",name);
        if(nodes.getLength()==0)nodes=doc.getElementsByTagName(name);
        return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();
    }
}
