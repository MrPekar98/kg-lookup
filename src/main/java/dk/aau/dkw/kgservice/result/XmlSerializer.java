package dk.aau.dkw.kgservice.result;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;

public class XmlSerializer extends AbstractSerializer implements Serializer
{
    public XmlSerializer(List<Result> resultSet)
    {
        super(resultSet);
    }

    @Override
    public String serialize()
    {
        try
        {
            int i = 1;
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.newDocument();
            Element root = document.createElement("output");
            document.appendChild(root);

            for (Result result : getRS())
            {
                Element child = document.createElement("entity-" + i++);
                child.setAttribute("entity", result.uri());
                child.setAttribute("label", result.label());
                child.setAttribute("description", result.description());
                child.setAttribute("score", String.valueOf(result.score()));
                root.appendChild(child);
            }

            DOMSource source = new DOMSource(document);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(source, new StreamResult(writer));

            return writer.toString();
        }

        catch (ParserConfigurationException | TransformerException e)
        {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
