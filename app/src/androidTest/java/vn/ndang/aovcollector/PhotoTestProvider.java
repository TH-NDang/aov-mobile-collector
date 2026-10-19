package vn.ndang.aovcollector;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Runs in the test APK's own process, with no dependency on the target app's Kotlin runtime. */
public class PhotoTestProvider extends DocumentsProvider {
    private static final String[] COLUMNS={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_FLAGS};
    private File directory() {
        File dir=new File(getContext().getFilesDir(),"photo-fixtures");dir.mkdirs();return dir;
    }
    @Override public boolean onCreate() { return true; }
    private void row(MatrixCursor cursor,String id) {
        boolean root=id.equals("Pictures");File file=root?directory():new File(directory(),id);
        Map<String,Object> values=new HashMap<>();
        values.put(Document.COLUMN_DOCUMENT_ID,id);values.put(Document.COLUMN_DISPLAY_NAME,id);
        values.put(Document.COLUMN_MIME_TYPE,root?Document.MIME_TYPE_DIR:id.endsWith(".zip")?"application/zip":"image/jpeg");
        values.put(Document.COLUMN_SIZE,file.length());values.put(Document.COLUMN_LAST_MODIFIED,file.lastModified());
        values.put(Document.COLUMN_FLAGS,root?Document.FLAG_DIR_SUPPORTS_CREATE:(Document.FLAG_SUPPORTS_WRITE|Document.FLAG_SUPPORTS_DELETE));
        MatrixCursor.RowBuilder row=cursor.newRow();for(String column:cursor.getColumnNames()) row.add(values.get(column));
    }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor=new MatrixCursor(new String[]{Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS});
        cursor.addRow(new Object[]{"photos","Pictures","Test Pictures",Root.FLAG_SUPPORTS_CREATE});return cursor;
    }
    @Override public Cursor queryDocument(String id,String[] projection) {
        MatrixCursor cursor=new MatrixCursor(projection==null?COLUMNS:projection);row(cursor,id);return cursor;
    }
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sortOrder) {
        if(!parent.equals("Pictures")) throw new IllegalArgumentException("Unexpected directory");
        MatrixCursor cursor=new MatrixCursor(projection==null?COLUMNS:projection);
        File[] files=directory().listFiles();if(files!=null) for(File file:files) if(file.isFile()) row(cursor,file.getName());
        return cursor;
    }
    @Override public String createDocument(String parent,String mime,String name) throws FileNotFoundException {
        if(!parent.equals("Pictures")||!new File(name).getName().equals(name)) throw new IllegalArgumentException("Unexpected name");
        try(FileOutputStream ignored=new FileOutputStream(new File(directory(),name))) { return name; }
        catch(IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if(!new File(id).getName().equals(id)) throw new IllegalArgumentException("Unexpected name");
        return ParcelFileDescriptor.open(new File(directory(),id),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public void deleteDocument(String id) { new File(directory(),id).delete(); }
    @Override public boolean isChildDocument(String parent,String id) { return parent.equals("Pictures"); }
}
