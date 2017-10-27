package com.ecjtu.sharebox.parcel;


import android.os.Parcel;

import com.ecjtu.parcel.base.ParcelableFileCacheHelper;
import com.ecjtu.sharebox.ui.holder.FileExpandableProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ethan_Xiang on 2017/10/11.
 */

public class FileExpandablePropertyCache extends ParcelableFileCacheHelper {

    public FileExpandablePropertyCache(String path) {
        super(path);
    }

    @Override
    protected <T> T readParcel(Parcel parcel) {
        int size = parcel.readInt();
        List<FileExpandableProperty> ret = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            List<String> childList = new ArrayList<>();
            parcel.readStringList(childList);
            String group = parcel.readString();
            boolean isActivated = (boolean) parcel.readValue(null);
            List<String> activatedList = new ArrayList<>();
            parcel.readStringList(activatedList);

            FileExpandableProperty child = new FileExpandableProperty(group, childList);
            child.setActivated(isActivated);
            child.setActivatedList(activatedList);

            ret.add(child);
        }
        return (T) ret;
    }

    @Override
    protected <T> Parcel writeParcel(Parcel parcel, T object) {
        if (!(object instanceof List) || ((List) object).size() <= 0 || !(((List) object).get(0) instanceof FileExpandableProperty)) {
            return null;
        }
        List list = (List) object;
        parcel.writeInt(list.size());
        for (int i = 0; i < list.size(); i++) {
            FileExpandableProperty vh = (FileExpandableProperty) list.get(i);
            List<String> childList = vh.getChildList();
            parcel.writeStringList(childList);
            parcel.writeString(vh.getGroup());
            parcel.writeValue(vh.isActivated());
            parcel.writeStringList(vh.getActivatedList());
        }
        return parcel;
    }
}