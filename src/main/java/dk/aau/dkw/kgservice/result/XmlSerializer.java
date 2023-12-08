package dk.aau.dkw.kgservice.result;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
                child.setAttribute("entity", result.item());
                child.setAttribute("score", String.valueOf(result.score()));
                root.appendChild(child);
            }

            return document.getTextContent();
        }

        catch (ParserConfigurationException e)
        {
            return ParserConfigurationException.class.getName() + ": " + e.getMessage();
        }
    }
}
