# kg-lookup
Lookup service for knowledge graphs (KG).

## Loading Knowledge Graph
Load the RDF files into a TDB model using the `load.sh` script:

```bash
./load.sh <KG-DIR>
```

Specify the directory in which the RDF data reside.
After running the script, the TDB files are stored in `tdb/`.

Now, construct the Lucene indexes using the loaded TDB model.

```bash
mkdir <LUCENE-DIR>
docker build -t kg-lookup .
docker run -v ${PWD}/<KG-DIR>:/tdb -v ${PWD}/<LUCENE-DIR> -p 7000:7000 --name kg-lookup-service kg-lookup
```

Substitute the <KG-DIR> and <LUCENE-DIR> placeholders with the values you have chosen.
Run with the `-d` flag to detach the container.

Finally, initiate a GET request to the `/index` endpoint using Curl to start the construction of the Lucene indexes.

```bash
curl localhost:7000/index
```

### Reconstructing Lucene Indexes
In case you want to reconstruct the Lucene indexes, simply redo the Curl instruction above.
The service will reconstruct the Lucene indexes using the latest TDB model, even if the service is still running.
So there is no need to stop the service and rebuild it.