package dk.aau.dkw.kgservice.index.build;

import java.io.File;

public abstract class AbstractBuilder
{
    private File dataDir;

    protected AbstractBuilder(File dataDir)
    {
        this.dataDir = dataDir;
    }

    protected File getDataDirectory()
    {
        return this.dataDir;
    }
}
