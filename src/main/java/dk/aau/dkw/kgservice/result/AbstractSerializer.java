package dk.aau.dkw.kgservice.result;

import java.util.List;

public abstract class AbstractSerializer
{
    private List<Result> rs;

    protected AbstractSerializer(List<Result> rs)
    {
        this.rs = rs;
    }

    protected List<Result> getRS()
    {
        return this.rs;
    }
}
