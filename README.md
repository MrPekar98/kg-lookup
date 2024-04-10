# kg-lookup
Lookup service for knowledge graphs (KG).

This lookup service builds a Lucene index over RDF data which enables performing keyword search over the KG entities.
Each KG entity is indexed with 6 Lucene fields that are used to describe the entity.
These fields include the entity URI, URI postfix, label, comment, description, and category.
Specifically, for each entity, this service constructs Lucene indexes by performing SPARQL queries to retrieve the literal values of the RDF predicates for these fields (except the URI and URI postfix).

During indexing of KG entities, all entity URIs containing *"Category:"* ro *"prop"* in the URI postfix are ignored, as it has been observed that these entities pollute the result set during keyword search.

During keyword search, a Boolean query is constructed.
This query consists of multiple sub-queries, one for each of the fields: entity label, URI postfix, comment, and category.
These fields are weighted, such that labels have weight 20.0, URI postfixes have 20.0, and comment and category each have the weight 0.1.
The weights are assigned in this way to avoid the likelihood that irrelevant entities are returned because the literal values of the returned entity comment and category mention another entity.
Each field query is also a Boolean query consisting of a term (exact match) or fuzzy query for each query token.
Both the inner and outer Boolean queries apply the Boolean *OR* operator between its query components.

## Loading Knowledge Graph
Load the RDF files into Virtuoso using the `load.sh` script:

```bash
./load.sh <KG-DIR> <GRAPH-NAME>
```

Specify the directory in which the RDF data reside and the name of the graph in which the graph will be stored.
A graph will be created into which the RDF data is inserted.

Note that only <ins>Turtle</ins> files are supported for now.

Now, construct the Lucene indexes using the loaded Virtuoso instance by first starting the service.

```bash
mkdir <LUCENE-DIR>
./run.sh <LUCENE-DIR> <GRAPH-NAME> <MIN-MEMORY-ALLOCATION> <KG-DIR>
```

Substitute the `LUCENE-DIR` with the relative path to the directory in which to store the Lucene indexes.
The value you have chosen for `GRAPH-NAME` when loading the graph previously should be used again.
Insert the minimum memory requirement in the `MIN-MEMORY-ALLOCATION` argument to be allocated for the service.
For example, you can specify to allocate 10GB of memory by passing `10g`.
Finally, pass the relative path to the directory containing the RDF files.

Finally, initiate a GET request to the `/index` endpoint using Curl to start the construction of the Lucene indexes.

```bash
curl http://localhost:7000/index
```

You can specify from which domain the entities must come by using the `domain` argument.
For example, you can specify that you only want to index entities from DBpedia by executing the following request with Curl.

```bash
curl http://localhost:7000/index?domain=dbpedia
```

You can retrieve the indexing log containing the number of inserted entities and the reason why some entities were skipped by copying the indexing log file to the current directory:

```bash
docker cp kg-lookup-servive:/logs/index.log .
```

To stop the service, hit `Ctrl+c`.

To stop Virtuoso, enter the following command.

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

The service also supports fuzzy keyword search, which can be enabled by using the parameter `fuzzy=true`.
By default, this parameter is false.

An example request to query using fuzzy search looks like the following:

```bash
curl http://localhost:7000/search?query=Barack%20Obama&fuzzy=true
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
