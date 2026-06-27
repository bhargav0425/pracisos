import { useState } from 'react';
import { UserInviteForm } from './UserInviteForm';
import { UserList } from './UserList';
import { useAuth } from '../../../shared/hooks/useAuth';
import { useDispatch } from 'react-redux';
import { logout } from '../slice';
import { useNavigate, useParams } from 'react-router-dom';
import { 
  LogOut, 
  Calendar, 
  Users, 
  Clock, 
  PlusCircle, 
  Info,
  Activity,
  CheckCircle2
} from 'lucide-react';
import { useGetTenantQuery } from '../api';
import { BookingForm } from '../../booking/components/BookingForm';
import { BookingList } from '../../booking/components/BookingList';
import { BookingDetailModal } from '../../booking/components/BookingDetailModal';
import { 
  useGetBookingsQuery, 
  useCancelBookingMutation, 
  useCompleteBookingMutation, 
  useMarkNoShowMutation,
  Booking 
} from '../../booking/api';

export function ClinicDashboard() {
  const { user } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<'appointments' | 'book' | 'staff'>(
    user?.role === 'CLINIC_OWNER' ? 'staff' : 'appointments'
  );
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { data: tenant } = useGetTenantQuery(tenantSlug || '', {
    skip: !tenantSlug || user?.role === 'SYSTEM_ADMIN',
  });

  const { data: bookings = [], refetch: refetchBookings } = useGetBookingsQuery(undefined, {
    skip: !user || user.role === 'SYSTEM_ADMIN'
  });

  const [cancelBooking] = useCancelBookingMutation();
  const [completeBooking] = useCompleteBookingMutation();
  const [markNoShow] = useMarkNoShowMutation();

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  const handleCancel = async (reason: string) => {
    if (selectedBooking) {
      await cancelBooking({ bookingId: selectedBooking.bookingId, reason }).unwrap();
      setIsModalOpen(false);
      refetchBookings();
    }
  };

  const handleComplete = async () => {
    if (selectedBooking) {
      await completeBooking(selectedBooking.bookingId).unwrap();
      setIsModalOpen(false);
      refetchBookings();
    }
  };

  const handleNoShow = async () => {
    if (selectedBooking) {
      await markNoShow(selectedBooking.bookingId).unwrap();
      setIsModalOpen(false);
      refetchBookings();
    }
  };

  const isOwner = user?.role === 'CLINIC_OWNER';
  const isPatient = user?.role === 'PATIENT';
  const isReceptionist = user?.role === 'RECEPTIONIST';
  const isPractitioner = user?.role === 'PRACTITIONER';

  return (
    <div className="min-h-screen p-6 md:p-10 text-slate-700 bg-[#f4f7f6]">
      <header className="flex flex-col md:flex-row md:items-center md:justify-between border-b border-slate-200 pb-6 mb-8 gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-teal-600">Clinic Portal</span>
            <span className="text-slate-400">•</span>
            <span className="text-xs font-semibold text-slate-500 tracking-wider">/{tenantSlug}</span>
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-slate-800 mt-1">
            {tenant ? tenant.name : user?.tenantSlug ? user.tenantSlug.toUpperCase().replace('-', ' ') : 'Practice Management'}
          </h1>
          <p className="text-slate-500 text-sm mt-0.5">Welcome back, {user?.fullName} (<span className="font-semibold text-teal-600">{user?.role}</span>)</p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={handleLogout}
            className="flex items-center px-4 py-2 bg-white border border-slate-200 hover:bg-slate-50 text-slate-600 hover:text-slate-800 rounded-xl shadow-sm transition-all active:scale-95 text-sm"
          >
            <LogOut className="w-4 h-4 mr-2" />
            Sign Out
          </button>
        </div>
      </header>

      {/* Navigation Tabs */}
      <div className="flex space-x-2 border-b border-slate-200 mb-8 overflow-x-auto pb-px">
        {isOwner && (
          <button
            onClick={() => setActiveTab('staff')}
            className={`flex items-center px-4 py-2.5 font-semibold text-sm border-b-2 transition-all ${
              activeTab === 'staff'
                ? 'border-teal-600 text-teal-600'
                : 'border-transparent text-slate-400 hover:text-slate-600'
            }`}
          >
            <Users className="w-4 h-4 mr-2" /> Staff Directory
          </button>
        )}

        <button
          onClick={() => setActiveTab('appointments')}
          className={`flex items-center px-4 py-2.5 font-semibold text-sm border-b-2 transition-all ${
            activeTab === 'appointments'
              ? 'border-teal-600 text-teal-600'
              : 'border-transparent text-slate-400 hover:text-slate-600'
          }`}
        >
          <Clock className="w-4 h-4 mr-2" /> Appointments
        </button>

        {(isPatient || isReceptionist) && (
          <button
            onClick={() => setActiveTab('book')}
            className={`flex items-center px-4 py-2.5 font-semibold text-sm border-b-2 transition-all ${
              activeTab === 'book'
                ? 'border-teal-600 text-teal-600'
                : 'border-transparent text-slate-400 hover:text-slate-600'
            }`}
          >
            <PlusCircle className="w-4 h-4 mr-2" /> Book Appointment
          </button>
        )}
      </div>

      {/* Tab Content */}
      <div className="space-y-6">
        {activeTab === 'staff' && isOwner && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-1">
              <UserInviteForm />
            </div>
            <div className="lg:col-span-2">
              <UserList />
            </div>
          </div>
        )}

        {activeTab === 'appointments' && (
          <div className="space-y-6">
            <div className="rounded-2xl border border-slate-200/80 bg-white p-6 md:p-8 shadow-sm">
              <h3 className="text-xl font-bold text-slate-800 mb-5 flex items-center">
                <Calendar className="w-5 h-5 mr-2.5 text-teal-600" /> 
                {isPatient ? 'My Appointments' : isPractitioner ? 'My Schedule' : 'All Clinic Bookings'}
              </h3>
              <BookingList 
                bookings={bookings} 
                onCancel={(booking) => {
                  setSelectedBooking(booking);
                  setIsModalOpen(true);
                }}
                onComplete={(booking) => {
                  setSelectedBooking(booking);
                  setIsModalOpen(true);
                }}
                onNoShow={(booking) => {
                  setSelectedBooking(booking);
                  setIsModalOpen(true);
                }}
                userRole={user?.role || ''}
              />
            </div>
          </div>
        )}

        {activeTab === 'book' && (isPatient || isReceptionist) && (
          <BookingForm />
        )}
      </div>

      {/* Detail & Action Modal */}
      <BookingDetailModal
        booking={selectedBooking}
        isOpen={isModalOpen}
        onClose={() => {
          setIsModalOpen(false);
          setSelectedBooking(null);
        }}
        onCancel={handleCancel}
        onComplete={handleComplete}
        onNoShow={handleNoShow}
        userRole={user?.role || ''}
      />
    </div>
  );
}
