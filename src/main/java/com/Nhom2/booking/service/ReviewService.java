package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Review;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.ReviewRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReviewService {

    static final int MIN_RATING = 1;
    static final int MAX_RATING = 10;

    @Autowired
    private ReviewRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    public List<Review> getAll() {
        return repository.findAll();
    }

    /**
     * Tạo review cho user đang đăng nhập. Author và hotel được lấy từ DB,
     * không tin dữ liệu user/hotel lồng trong request body.
     */
    public Review create(String username, Review request) {
        if (request == null || request.getHotel() == null || request.getHotel().getId() == null) {
            throw new RuntimeException("Hotel id is required");
        }
        if (request.getRating() == null || request.getRating() < MIN_RATING || request.getRating() > MAX_RATING) {
            throw new RuntimeException("Rating must be between " + MIN_RATING + " and " + MAX_RATING);
        }

        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Hotel hotel = hotelRepository.findById(request.getHotel().getId())
                .orElseThrow(() -> new NotFoundException("Hotel not found"));

        Review review = new Review();
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setUser(author);
        review.setHotel(hotel);
        return repository.save(review);
    }
}
