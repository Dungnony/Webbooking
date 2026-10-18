package com.Nhom2.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "wishlists",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlists_user_hotel", columnNames = {"user_id", "hotel_id"}))
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Wishlist(User user, Hotel hotel) {
        this.user = user;
        this.hotel = hotel;
    }
}
