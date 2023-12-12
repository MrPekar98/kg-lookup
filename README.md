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
docker run -it -v ${PWD}/tdb/:/tdb -v ${PWD}/<LUCENE-DIR>:/lucene -p 7000:7000 --name kg-lookup-service kg-lookup
```

Substitute the <LUCENE-DIR> placeholder with the value you have chosen.
Run with the `-d` flag to detach the container.

Finally, initiate a GET request to the `/index` endpoint using Curl to start the construction of the Lucene indexes.

```bash
curl http://localhost:7000/index
```

To stop the service, hit `Ctrl+c`.

### Reconstructing Lucene Indexes
In case you want to reconstruct the Lucene indexes, simply redo the Curl instruction above.
The service will reconstruct the Lucene indexes using the latest TDB model, even if the service is still running.
So there is no need to stop the service and rebuild it.

## Running the Service
If you haven't stopped the service after setup, you can start performing lookups on the URL `http://localhost:7000/search?query=<QUERY>`.
To specify the result set size, add the `k` option as in `http://localhost:7000/search?query=<QUERY>&k=<K>`.
The default value of `k` is 10.
The system supports output formats in JSON and XML: `http://localhost:7000/search?query=<QUERY>&k=<K>&format=<FORMAT>`.
The default format is JSON.

For example, if you want to lookup 25 KG entities using the query `Barack Obama` in XML format, you can do so with the following Curl command:

```bash
curl http:/localhost:7000/search?query=Barack%20Obama&k=25&format=xml
```

In case you have stopped the service after setup, you can restart it with the following command:

```bash
docker start kg-lookup-service
```