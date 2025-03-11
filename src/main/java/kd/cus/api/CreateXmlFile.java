package kd.cus.api;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

public class CreateXmlFile {

    public static Document createDocument() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        return documentBuilder.newDocument();
    }

    public static Element rootElement(Document document, String rootName) {
        Element element = document.createElement(rootName);
        document.appendChild(element);
        return element;
    }

    public static Element docCreateElement(Document document, String elementName) {
        return document.createElement(elementName);
    }

    public static void parentAddChild(Document document, Element parentElement, Element childElment) {
        parentElement.appendChild(childElment);
    }

    public static void addValueToElement(Document document, Element element, String attrName, String attrValue) {
        Element name = document.createElement(attrName);
        name.appendChild(document.createTextNode(attrValue));
        element.appendChild(name);
    }

    public static void setAttToElement(Document document, Element element, String attrName, String attrValue){
        element.setAttribute(attrName, attrValue);
    }

    public static String docToString(Document document) throws TransformerException {
        String xmlStr = "";
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("encoding", "UTF-8");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(byteArrayOutputStream));
        xmlStr = byteArrayOutputStream.toString();
        return xmlStr;
    }
}
