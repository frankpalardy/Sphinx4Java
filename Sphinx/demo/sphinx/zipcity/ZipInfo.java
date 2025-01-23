// Decompiled by Jad v1.5.7f. Copyright 2000 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/SiliconValley/Bridge/8617/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   ZipDatabase.java

package demo.sphinx.zipcity;


class ZipInfo
{

    ZipInfo(String zip, String city, String state, float lat, float longitude)
    {
        this.zip = zip;
        this.city = city;   
        this.state = state;
        latitude = lat;
        this.longitude = longitude;
    }

    public String getZip()
    {
        return zip;
    }

    public String getCity()
    {
        return city;
    }

    public String getState()
    {
        return state;
    }

    public float getLatitude()
    {
        return latitude;
    }

    public float getLongitude()
    {
        return longitude;
    }

    public String toString()
    {
        return "Zip: " + getZip() + " City: " + getCity() + " State: " + getState() + "  " + getLatitude() + "," + getLongitude();
    }

    private String zip;
    private String city;
    private String state;
    private float latitude;
    private float longitude;
}
