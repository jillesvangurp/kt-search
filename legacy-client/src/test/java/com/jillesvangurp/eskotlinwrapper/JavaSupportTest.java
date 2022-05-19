package com.jillesvangurp.eskotlinwrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.KotlinExtensionsKt;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class JavaSupportTest {
    private IndexRepository<TestBean> dao;

    @BeforeEach
    public void before() {
        // note this test merely proofs it is possible to use things from Java. I may at some point do some work to make this nicer/easier because this code is ugly.
        // pull requests/suggestions welcome. Please keep in mind that I want to keep things clean for Kotlin users.

        RestClientBuilder builder = RestClient.builder(new HttpHost("localhost", 9999, "http"));
        RestHighLevelClient restHighLevelClient = new RestHighLevelClient(builder);
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonModelReaderAndWriter<TestBean> jacksonModelReaderAndWriter = new JacksonModelReaderAndWriter<>(
                TestBean.class,
                objectMapper
        );
        // no extension functions but they can still be used
        String index = "test-" + randomId();
        dao = KotlinExtensionsKt.createIndexRepository(restHighLevelClient, index,jacksonModelReaderAndWriter,"_doc",index,index,true, FetchSourceContext.FETCH_SOURCE, RequestOptions.DEFAULT);
    }

    @Test
    public void shouldDoCrud() {
        String id = randomId();
        TestBean testBean = getBean("foo");
        dao.index(id,testBean,true,null,null, WriteRequest.RefreshPolicy.WAIT_UNTIL, RequestOptions.DEFAULT, null);
        TestBean fromIndex = dao.get(id);
        Assertions.assertEquals(fromIndex.getMessage(),"foo");
    }

    @Test
    public void shouldDoBulk() {
        Function2<BulkOperation<TestBean>, BulkItemResponse, Unit> callback = (o, r) -> Unit.INSTANCE;

        try(BulkIndexingSession<TestBean> bulkIndexer = dao.bulkIndexer(10, 2, WriteRequest.RefreshPolicy.WAIT_UNTIL, callback,RequestOptions.DEFAULT)) {
            for(int i=0;i<42;i++)
            bulkIndexer.index(randomId(),getBean("hello "+i),true,callback);
        }
        dao.refresh();

        SearchResults<TestBean> results = dao.search(true, 1l, RequestOptions.DEFAULT,searchRequest -> {
            searchRequest.source(SearchSourceBuilder.searchSource().query(new MatchAllQueryBuilder()));
            return Unit.INSTANCE;
        });
        results.getMappedHits().iterator().forEachRemaining(b -> {
            Assertions.assertTrue(b.getMessage().startsWith("hello"));
        });
    }

    @NotNull
    public static String randomId() {
        return UUID.randomUUID().toString();
    }

    @NotNull
    private TestBean getBean(String m) {
        TestBean testBean = new TestBean();
        testBean.setMessage(m);
        return testBean;
    }
}
