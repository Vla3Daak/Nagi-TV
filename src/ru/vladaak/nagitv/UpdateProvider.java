package ru.vladaak.nagitv;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
/** A single-file, read-only content provider. Never exposes keys or wallpaper memory. */
public final class UpdateProvider extends ContentProvider{
    public boolean onCreate(){return true;}
    private File file(Uri u)throws FileNotFoundException{
        if(!"ru.vladaak.nagitv.updates".equals(u.getAuthority())||!"/update.apk".equals(u.getPath())||u.getQuery()!=null)throw new FileNotFoundException();
        File f=new File(getContext().getFilesDir(),"updates/update.apk");if(!f.isFile())throw new FileNotFoundException();return f;
    }
    public String getType(Uri uri){return "application/vnd.android.package-archive";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri u,String[] projection,String s,String[] args,String sort){
        try{File f=file(u);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?"Nagi-TV-update.apk":OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(values);return c;}catch(Exception e){return null;}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
