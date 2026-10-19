package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByUserId(Long userId);

    /** Each row is {ChatRoom, Long unreadFromOwner}, most recently active first, in a single query. */
    @Query("""
            select r,
                   (select count(m) from ChatMessage m
                    where m.room = r and m.readAt is null and m.sender = r.user)
            from ChatRoom r
            order by r.lastMessageAt desc nulls last
            """)
    List<Object[]> findAllWithUnreadFromOwner();
}
