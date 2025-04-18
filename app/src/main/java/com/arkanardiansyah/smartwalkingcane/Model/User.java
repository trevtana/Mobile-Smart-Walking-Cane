package com.arkanardiansyah.smartwalkingcane.Model;

public class User {
    private String email;
    private String foto_profil;
    private String gender;
    private String nama;
    private String no_telp;
    private String product_id;
    private String tinggi_badan;
    private int usia;

    // Constructor, getters dan setters
    public User() { }

    public User(String email, String foto_profil, String gender, String nama, String no_telp, String product_id, String tinggi_badan, int usia) {
        this.email = email;
        this.foto_profil = foto_profil;
        this.gender = gender;
        this.nama = nama;
        this.no_telp = no_telp;
        this.product_id = product_id;
        this.tinggi_badan = tinggi_badan;
        this.usia = usia;
    }

    public String getEmail() { return email; }
    public String getFoto_profil() { return foto_profil; }
    public String getGender() { return gender; }
    public String getNama() { return nama; }
    public String getNo_telp() { return no_telp; }
    public String getProduct_id() { return product_id; }
    public String getTinggi_badan() { return tinggi_badan; }
    public int getUsia() { return usia; }
}

