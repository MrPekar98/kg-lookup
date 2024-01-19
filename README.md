# kg-lookup
Lookup service for knowledge graphs (KG).

## Loading Knowledge Graph
Load the RDF files into Virtuoso using the `load.sh` script:

```bash
./load.sh <KG-DIR> <GRAPH-NAME>
```

Specify the directory in which the RDF data reside and the name of the graph in which the graph will be stored.
A graph will be created into which the RDF data is inserted.

Note that only Turtle files are supported for now.

Now, construct the Lucene indexes using the loaded Virtuoso instance.

```bash
mkdir <LUCENE-DIR>
./run.sh <LUCENE-DIR> <GRAPH-NAME> <MIN MEMORY ALLOCATION>
```

Substitute the `LUCENE-DIR` placeholder with the value you have chosen and `GRAPH-NAME` with the graph name used when loading the graph.
Insert the minimum memory requirement in the `MEM` argument to be allocated for the service.
For example, you can specify to allocate 10GB of memory by passing `MEM=10g`.

Finally, initiate a GET request to the `/index` endpoint using Curl to start the construction of the Lucene indexes.

```bash
curl http://localhost:7000/index
```

To stop the service, hit `Ctrl+c`.

To stop Virtuoso, enter the following commond.

```bash
docker stop vos
```

Constructing Lucene indexes cannot be performed without Virtuoso running.
If you have stopped Virtuoso and wish to re-construct Lucene indexes, run the following command to restart Virtuoso.

```bash
docker run --rm --name vos -d --network kg-lookup-network \
           -v ${PWD}/database:/database \
           -v ${PWD}/import:/import \
           -t -p 1111:1111 -p 8890:8890 \
           -i openlink/virtuoso-opensource-7:7
```

### Reconstructing Lucene Indexes
In case you want to reconstruct the Lucene indexes, simply redo the Curl instruction above.
The service will reconstruct the Lucene indexes using the running Virtuoso instance, even if the service is still running.
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

# Developers
Clone the Virtuoso client driver from GitHub.

```bash
git clone https://github.com/srdc/virt-jena.git
```

Now, install the driver with Maven (make sure to have Maven installed on the machine).

```bash
cd virt-jena
mvn clean install
cd ..
```

You are now good to go!
