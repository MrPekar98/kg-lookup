package dk.aau.dkw.kgservice.result;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class JsonSerializer extends AbstractSerializer implements Serializer
{
    public JsonSerializer(List<Result> resultSet)
    {
        super(resultSet);
    }

    @Override
    public String serialize()
    {
        JSONObject results = new JSONObject();
        JSONArray array = new JSONArray();

        for (Result result : getRS())
        {
            JSONObject obj = new JSONObject();
            obj.put("entity", result.uri());
            obj.put("label", result.label());
            obj.put("description", result.description());
            obj.put("score", result.score());
            array.put(obj);
        }

        results.put("output", array);
        return results.toString();
    }
}
