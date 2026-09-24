package cn.qwenpaw.android;

import org.junit.Test;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.io.ByteArrayOutputStream;
import static org.junit.Assert.*;

public class AttachmentPreviewTest {
    @Test public void markdownUsesDocumentExtensionOnly(){
        assertTrue(AttachmentPreview.markdown("REPORT.MD?download=1"));
        assertTrue(AttachmentPreview.markdown("report.markdown"));
        assertFalse(AttachmentPreview.markdown("report.txt"));
    }
    @Test public void formatsNestedJsonObjectsAndArrays(){
        String source="{\"report\":{\"days\":[1,2],\"title\":\"日报\"}}";
        String pretty=AttachmentPreview.formatText("report.json",source);
        assertTrue(pretty.contains("\n"));assertTrue(pretty.contains("\"report\": {"));
        assertTrue(pretty.contains("  \"days\": ["));
        assertEquals(Json.object(Json.parse(source)).toString(),Json.object(Json.parse(pretty)).toString());
        assertEquals("[\n  1,\n  2\n]",AttachmentPreview.formatText("list.JSON","[1,2]"));
    }
    @Test public void keepsInvalidJsonAndOtherTextReadable(){
        for(String source:new String[]{"{broken","{\"a\":1} trailing","null","\"hello\""})
            assertEquals(source,AttachmentPreview.formatText("data.json",source));
        assertEquals("{\"a\":1}",AttachmentPreview.formatText("data.txt","{\"a\":1}"));
    }
    @Test public void classifiesSupportedFiles() {
        assertEquals(AttachmentPreview.Kind.TEXT,AttachmentPreview.kind("AGENTS.md"));
        assertEquals(AttachmentPreview.Kind.TEXT,AttachmentPreview.kind("data.JSON"));
        assertEquals(AttachmentPreview.Kind.TEXT,AttachmentPreview.kind("script.py"));
        assertEquals(AttachmentPreview.Kind.IMAGE,AttachmentPreview.kind("photo.PNG"));
        assertEquals(AttachmentPreview.Kind.EXTERNAL,AttachmentPreview.kind("clip.mp4"));
        assertEquals(AttachmentPreview.Kind.EXTERNAL,AttachmentPreview.kind("page.html"));
        assertEquals(AttachmentPreview.Kind.UNSUPPORTED,AttachmentPreview.kind("report.docx"));
        assertEquals(AttachmentPreview.Kind.UNSUPPORTED,AttachmentPreview.kind("bundle.zip"));
    }
    @Test public void limitsAndMime() {
        assertEquals(3145728L,AttachmentPreview.TEXT_LIMIT);
        assertEquals("text/html",AttachmentPreview.mime("page.HTM"));
        assertEquals("video/mp4",AttachmentPreview.mime("clip.mp4"));
    }
    @Test public void rejectsDeclaredOversizeBeforeWriting() throws Exception {
        try(MockWebServer server=new MockWebServer()){
            server.enqueue(new MockResponse().setBody("1234567890"));server.start();
            ApiClient api=new ApiClient(server.url("").toString().replaceAll("/$",""),"","agent");ByteArrayOutputStream out=new ByteArrayOutputStream();
            assertThrows(ApiClient.FileTooLargeException.class,()->api.downloadBounded("test.txt",out,5));
            assertEquals(0,out.size());
        }
    }
    @Test public void rejectsChunkedOversizeDuringStreaming() throws Exception {
        try(MockWebServer server=new MockWebServer()){
            server.enqueue(new MockResponse().setChunkedBody("1234567890",2));server.start();
            ApiClient api=new ApiClient(server.url("").toString().replaceAll("/$",""),"","agent");ByteArrayOutputStream out=new ByteArrayOutputStream();
            assertThrows(ApiClient.FileTooLargeException.class,()->api.downloadBounded("test.txt",out,5));
            assertTrue(out.size()<=5);
        }
    }
}
