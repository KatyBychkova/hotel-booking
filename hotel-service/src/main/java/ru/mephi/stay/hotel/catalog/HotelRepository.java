package ru.mephi.stay.hotel.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

interface HotelRepository extends JpaRepository<Hotel, Long> {}
