import type { Booking, Room } from '../api-generated';
import type { HotelWithStats } from '../api';

export interface GuestInfo {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  requests?: string;
}

/** Router state passed from GuestInfoPage → PaymentPage → BookingConfirmation. */
export interface BookingFlowState {
  booking: Booking;
  hotel: HotelWithStats | null;
  guestInfo: GuestInfo;
  room: Room;
  checkIn: string;
  checkOut: string;
  nights: number;
  totalPrice: number;
}
