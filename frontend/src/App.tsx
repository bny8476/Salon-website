import React, { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useThemeStore } from './store/useThemeStore';
import { motion, AnimatePresence, MotionConfig } from 'framer-motion';
import { Toaster } from './components/ui/Toaster';

// Layouts
import { AdminLayout } from './components/layout/AdminLayout';
import { StaffLayout } from './components/layout/StaffLayout';
import { CustomerLayout } from './components/layout/CustomerLayout';
import { ManagerLayout } from './components/layout/ManagerLayout';

// Public Pages
const Home = React.lazy(() => import('./pages/Home').then(m => ({ default: m.Home })));
const Login = React.lazy(() => import('./pages/Login').then(m => ({ default: m.Login })));
const Register = React.lazy(() => import('./pages/Register').then(m => ({ default: m.Register })));
const ForgotPassword = React.lazy(() => import('./pages/auth/ForgotPassword').then(m => ({ default: m.ForgotPassword })));
const ResetPassword = React.lazy(() => import('./pages/auth/ResetPassword').then(m => ({ default: m.ResetPassword })));
const NotFound = React.lazy(() => import('./pages/NotFound').then(m => ({ default: m.NotFound })));
const Services = React.lazy(() => import('./pages/Services'));
const About = React.lazy(() => import('./pages/About'));
const FAQ = React.lazy(() => import('./pages/FAQ'));
const Gallery = React.lazy(() => import('./pages/Gallery'));
const Testimonials = React.lazy(() => import('./pages/Testimonials'));
const Blog = React.lazy(() => import('./pages/Blog'));
const BlogPost = React.lazy(() => import('./pages/BlogPost'));
const Careers = React.lazy(() => import('./pages/Careers'));
const Franchise = React.lazy(() => import('./pages/Franchise'));
const GiftCards = React.lazy(() => import('./pages/GiftCards'));
const Wallet = React.lazy(() => import('./pages/Wallet'));
const Referrals = React.lazy(() => import('./pages/Referrals'));
import { ErrorBoundary } from './components/errors/ErrorBoundary';
import { ChatWidget } from './components/ui/ChatWidget';

// Customer Flow (No Layout initially, then Layout)
const CustomerBooking = React.lazy(() => import('./pages/CustomerBooking').then(m => ({ default: m.CustomerBooking })));
const CategoryServices = React.lazy(() => import('./pages/CategoryServices').then(m => ({ default: m.CategoryServices })));

// Dashboards & Auth Pages
const CustomerDashboard = React.lazy(() => import('./pages/customer/Dashboard').then(m => ({ default: m.Dashboard })));
const CustomerPayments = React.lazy(() => import('./pages/customer/Payments').then(m => ({ default: m.Payments })));
const CustomerServices = React.lazy(() => import('./pages/customer/Services').then(m => ({ default: m.Services })));
const CustomerProducts = React.lazy(() => import('./pages/customer/Products').then(m => ({ default: m.Products })));
const CustomerMembership = React.lazy(() => import('./pages/customer/Membership').then(m => ({ default: m.Membership })));
const CustomerProfile = React.lazy(() => import('./pages/customer/Profile').then(m => ({ default: m.Profile })));

const AdminDashboard = React.lazy(() => import('./pages/admin/AdminDashboard').then(m => ({ default: m.AdminDashboard })));
const StaffDashboard = React.lazy(() => import('./pages/staff/Dashboard').then(m => ({ default: m.Dashboard })));
const TodaysAppointments = React.lazy(() => import('./pages/staff/TodaysAppointments').then(m => ({ default: m.TodaysAppointments })));
const StaffCustomers = React.lazy(() => import('./pages/staff/Customers').then(m => ({ default: m.StaffCustomers })));
const LeaveRequests = React.lazy(() => import('./pages/staff/LeaveRequests').then(m => ({ default: m.LeaveRequests })));
const StaffDirectory = React.lazy(() => import('./pages/staff/StaffDirectory').then(m => ({ default: m.StaffDirectory })));
const StaffReports = React.lazy(() => import('./pages/staff/Reports').then(m => ({ default: m.Reports })));

// Existing Admin Pages
const Customers = React.lazy(() => import('./pages/admin/Customers').then(m => ({ default: m.Customers })));
const Staff = React.lazy(() => import('./pages/admin/Staff').then(m => ({ default: m.Staff })));
const Billing = React.lazy(() => import('./pages/admin/Billing').then(m => ({ default: m.Billing })));
const AdminProducts = React.lazy(() => import('./pages/admin/Products').then(m => ({ default: m.AdminProducts })));
const AdminServices = React.lazy(() => import('./pages/admin/Services').then(m => ({ default: m.AdminServices })));
const Suppliers = React.lazy(() => import('./pages/admin/Suppliers').then(m => ({ default: m.Suppliers })));
const Expenses = React.lazy(() => import('./pages/admin/Expenses').then(m => ({ default: m.Expenses })));
const Branches = React.lazy(() => import('./pages/admin/Branches').then(m => ({ default: m.Branches })));
const BranchComparison = React.lazy(() => import('./pages/admin/BranchComparison').then(m => ({ default: m.BranchComparison })));
const CmsEditor = React.lazy(() => import('./pages/admin/CmsEditor').then(m => ({ default: m.CmsEditor })));
const ScheduleBuilder = React.lazy(() => import('./pages/admin/ScheduleBuilder').then(m => ({ default: m.ScheduleBuilder })));
const LiveAttendance = React.lazy(() => import('./pages/admin/LiveAttendance').then(m => ({ default: m.LiveAttendance })));
const AdminSettings = React.lazy(() => import('./pages/admin/Settings').then(m => ({ default: m.Settings })));
const AdminGiftCards = React.lazy(() => import('./pages/admin/GiftCards'));
const StaffSchedule = React.lazy(() => import('./pages/staff/StaffSchedule').then(m => ({ default: m.StaffSchedule })));
const StaffAttendance = React.lazy(() => import('./pages/staff/StaffAttendance').then(m => ({ default: m.StaffAttendance })));
const Payroll = React.lazy(() => import('./pages/staff/Payroll').then(m => ({ default: m.Payroll })));
const ManagerReports = React.lazy(() => import('./pages/manager/Reports').then(m => ({ default: m.Reports })));
const ManagerDashboard = React.lazy(() => import('./pages/manager/ManagerDashboard').then(m => ({ default: m.ManagerDashboard })));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      refetchOnWindowFocus: false,
      refetchOnMount: false,
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 30 * 60 * 1000, // 30 minutes
    },
  },
});

import { ProtectedRoute } from './components/routing/ProtectedRoute';


const AnimatedRoutes = () => {
  const location = useLocation();
  return (
    <Routes location={location}>
        {/* Public Routes */}
        <Route path="/" element={<PageTransition><Home /></PageTransition>} />
        <Route path="/login" element={<PageTransition><Login /></PageTransition>} />
        <Route path="/admin/login" element={<PageTransition><Login /></PageTransition>} />
        <Route path="/staff/login" element={<PageTransition><Login /></PageTransition>} />
        <Route path="/register" element={<PageTransition><Register /></PageTransition>} />
        <Route path="/forgot-password" element={<PageTransition><ForgotPassword /></PageTransition>} />
        <Route path="/reset-password" element={<PageTransition><ResetPassword /></PageTransition>} />
        <Route path="/book" element={<PageTransition><CustomerBooking /></PageTransition>} />
        <Route path="/book/category/:categoryName" element={<PageTransition><CategoryServices /></PageTransition>} />
        
        {/* Customer Routes (Guest Portal) */}
        <Route 
          path="/customer" 
          element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <CustomerLayout />
            </ProtectedRoute>
          } 
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<PageTransition><CustomerDashboard /></PageTransition>} />
          <Route path="services" element={<PageTransition><CustomerServices /></PageTransition>} />
          <Route path="products" element={<PageTransition><CustomerProducts /></PageTransition>} />
          <Route path="membership" element={<PageTransition><CustomerMembership /></PageTransition>} />
          <Route path="payments" element={<PageTransition><CustomerPayments /></PageTransition>} />
          <Route path="profile" element={<PageTransition><CustomerProfile /></PageTransition>} />
          <Route path="gift-cards" element={<PageTransition><GiftCards /></PageTransition>} />
          <Route path="wallet" element={<PageTransition><Wallet /></PageTransition>} />
          <Route path="referrals" element={<PageTransition><Referrals /></PageTransition>} />
        </Route>

        {/* Staff Routes */}
        <Route 
          path="/staff" 
          element={
            <ProtectedRoute allowedRoles={['STAFF', 'MANAGER', 'ADMIN', 'RECEPTIONIST', 'THERAPIST']}>
              <StaffLayout />
            </ProtectedRoute>
          } 
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<PageTransition><StaffDashboard /></PageTransition>} />
          <Route path="appointments" element={<PageTransition><TodaysAppointments /></PageTransition>} />
          <Route path="customers" element={<PageTransition><StaffCustomers /></PageTransition>} />
          <Route path="leave-requests" element={<PageTransition><LeaveRequests /></PageTransition>} />
          <Route path="schedule" element={<PageTransition><StaffSchedule /></PageTransition>} />
          <Route path="attendance" element={<PageTransition><StaffAttendance /></PageTransition>} />
          <Route path="payroll" element={<PageTransition><Payroll /></PageTransition>} />
          <Route path="directory" element={<PageTransition><StaffDirectory /></PageTransition>} />
          <Route path="reports" element={<PageTransition><StaffReports /></PageTransition>} />
        </Route>

        {/* Public marketing pages */}
        <Route path="/services" element={<PageTransition><Services /></PageTransition>} />
        <Route path="/about" element={<PageTransition><About /></PageTransition>} />
        <Route path="/faq" element={<PageTransition><FAQ /></PageTransition>} />
        <Route path="/gallery" element={<PageTransition><Gallery /></PageTransition>} />
        <Route path="/testimonials" element={<PageTransition><Testimonials /></PageTransition>} />
        <Route path="/blog" element={<PageTransition><Blog /></PageTransition>} />
        <Route path="/blog/:slug" element={<PageTransition><BlogPost /></PageTransition>} />
        <Route path="/careers" element={<PageTransition><Careers /></PageTransition>} />
        <Route path="/franchise" element={<PageTransition><Franchise /></PageTransition>} />

        {/* Admin Routes */}
        <Route 
          path="/admin" 
          element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminLayout />
            </ProtectedRoute>
          } 
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<PageTransition><AdminDashboard /></PageTransition>} />
          <Route path="customers" element={<PageTransition><Customers /></PageTransition>} />
          <Route path="staff" element={<PageTransition><Staff /></PageTransition>} />
          <Route path="services" element={<PageTransition><AdminServices /></PageTransition>} />
          <Route path="products" element={<PageTransition><AdminProducts /></PageTransition>} />
          <Route path="suppliers" element={<PageTransition><Suppliers /></PageTransition>} />
          <Route path="expenses" element={<PageTransition><Expenses /></PageTransition>} />
          <Route path="payroll" element={<PageTransition><Payroll /></PageTransition>} />
          <Route path="schedule" element={<PageTransition><ScheduleBuilder /></PageTransition>} />
          <Route path="attendance" element={<PageTransition><LiveAttendance /></PageTransition>} />
          <Route path="gift-cards" element={<PageTransition><AdminGiftCards /></PageTransition>} />
          <Route path="inventory" element={<Navigate to="../products" replace />} />
          <Route path="reports" element={<PageTransition><Billing /></PageTransition>} />
          <Route path="branches" element={<PageTransition><Branches /></PageTransition>} />
          <Route path="branch-comparison" element={<PageTransition><BranchComparison /></PageTransition>} />
          <Route path="cms" element={<PageTransition><CmsEditor /></PageTransition>} />
          <Route path="settings" element={<PageTransition><AdminSettings /></PageTransition>} />
        </Route>

        {/* Manager Routes */}
        <Route 
          path="/manager" 
          element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <ManagerLayout />
            </ProtectedRoute>
          } 
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<PageTransition><ManagerDashboard /></PageTransition>} />
          <Route path="customers" element={<PageTransition><Customers /></PageTransition>} />
          <Route path="staff" element={<PageTransition><Staff /></PageTransition>} />
          <Route path="appointments" element={<PageTransition><ScheduleBuilder /></PageTransition>} />
          <Route path="revenue" element={<PageTransition><Billing /></PageTransition>} />
          <Route path="inventory" element={<PageTransition><AdminProducts /></PageTransition>} />
          <Route path="reports" element={<PageTransition><ManagerReports /></PageTransition>} />
        </Route>
        
        {/* Fallbacks */}
        <Route path="/unauthorized" element={<div className="flex h-screen items-center justify-center font-serif text-2xl text-primary">Unauthorized Access</div>} />
        <Route path="*" element={<NotFound />} />
      </Routes>
  );
};

const PageTransition = ({ children }: { children: React.ReactNode }) => {
  const location = useLocation();
  return (
    <React.Suspense fallback={
      <div className="flex h-[60vh] w-full items-center justify-center">
        <span className="material-symbols-outlined animate-spin text-primary text-4xl">progress_activity</span>
      </div>
    }>
      <AnimatePresence mode="wait">
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -8 }}
        transition={{ duration: 0.25, ease: 'easeOut' }}
        className="w-full"
      >
        {children}
        </motion.div>
      </AnimatePresence>
    </React.Suspense>
  );
};

const SplashScreen = () => {
  return (
    <motion.div
      initial={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.8, ease: "easeInOut" }}
      className="fixed inset-0 z-[100] flex items-center justify-center bg-background pointer-events-none overflow-hidden"
      aria-hidden="true"
    >
      <div className="absolute inset-0 z-0">
        <div
          className="w-full h-full bg-cover bg-center"
          style={{
            backgroundImage:
              "url('https://lh3.googleusercontent.com/aida-public/AB6AXuCG7wV3dL6UOVMLkuAFaUcs6zEMQxwsUAMsQmSlJH0GdvlbkVD7B795DzeFx5xp2E7H7VsnqHywkJV9K4Umkrm9zqOQzZC4PEZ3-lcsGMYRLCCivOBlIyrYvuaZZSXivkRxqN92euuAe3hpHR-ZmvrLIXW_hRvCysF_eOzrSHorpmGcjA2gT0HtPvc1Cynl1CCUo1OnQq65d7XzBcP59M6pekm-8bQLofi8bkQFbE2yWkO-Dw2pyxtsrCaORBng6W8An5S2nXXQ9Nqs')",
          }}
        />
        <div className="absolute inset-0 luxury-overlay z-10 backdrop-blur-[2px]" />
      </div>

      <motion.div
        initial={{ opacity: 0, scale: 0.9, filter: 'blur(10px)' }}
        animate={{ opacity: 1, scale: 1, filter: 'blur(0px)' }}
        exit={{ opacity: 0, scale: 1.1, filter: 'blur(10px)' }}
        transition={{ duration: 1.2, ease: "easeOut" }}
        className="relative z-20 flex flex-col items-center"
      >
        <h1 className="font-display-lg text-4xl md:text-6xl text-primary tracking-widest uppercase shadow-sm">
          Lumina
        </h1>
        <motion.div 
          initial={{ scaleX: 0 }}
          animate={{ scaleX: 1 }}
          transition={{ delay: 0.5, duration: 0.8, ease: "easeInOut" }}
          className="h-[1px] w-full bg-primary/50 mt-4 origin-center"
        />
      </motion.div>
    </motion.div>
  );
};

function App() {
  const { theme } = useThemeStore();
  const [showSplash, setShowSplash] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setShowSplash(false), 2000);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [theme]);

  useEffect(() => {
    // Always use the relative path so the Vercel proxy handles routing.
    // This makes the request same-origin from the browser's perspective,
    // ensuring cookies are always sent without any SameSite restrictions.
    const baseUrl = (import.meta.env.VITE_API_BASE_URL as string || '').replace(/\/$/, '');
    const eventSource = new EventSource(`${baseUrl}/api/v1/events/stream`, { withCredentials: true });

    eventSource.addEventListener('appointment_booked', () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['myNotifications'] });
    });

    eventSource.addEventListener('appointment_updated', () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['myNotifications'] });
    });

    eventSource.addEventListener('new_notification', () => {
      queryClient.invalidateQueries({ queryKey: ['myNotifications'] });
    });

    return () => {
      eventSource.close();
    };
  }, []);



  return (
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        <ErrorBoundary>
          <React.Suspense fallback={<div className="flex h-screen items-center justify-center">Loading...</div>}>
          <BrowserRouter>
            <div className="min-h-screen bg-background text-on-background font-sans transition-colors duration-300">
              <AnimatePresence>
                {showSplash && <SplashScreen />}
              </AnimatePresence>
              <AnimatedRoutes />
              <Toaster />
            </div>
            <ChatWidget />
          </BrowserRouter>
        </React.Suspense>
      </ErrorBoundary>
      </MotionConfig>
    </QueryClientProvider>
  );
}

export default App;
