import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axiosClient from '../../api/axiosClient';
import { AnimatePresence, motion } from 'framer-motion';
import { toast } from './use-toast';

interface Notification {
  id: number;
  title: string;
  message: string;
  type: string;
  isRead: boolean;
  createdAt: string;
}

const typeIcon: Record<string, string> = {
  APPOINTMENT_BOOKED: 'event_available',
  APPOINTMENT_REMINDER: 'alarm',
  APPOINTMENT_CANCELLED: 'event_busy',
  LOYALTY_EARNED: 'loyalty',
  GIFT_CARD_RECEIVED: 'redeem',
  GENERAL: 'notifications',
  SHIFT_REMINDER: 'schedule',
  LATE_ALERT: 'warning',
  SWAP_REQUEST: 'swap_horiz',
};

const typeColor: Record<string, string> = {
  APPOINTMENT_BOOKED: 'text-green-500',
  APPOINTMENT_REMINDER: 'text-blue-500',
  APPOINTMENT_CANCELLED: 'text-red-500',
  LOYALTY_EARNED: 'text-amber-500',
  GIFT_CARD_RECEIVED: 'text-purple-500',
  GENERAL: 'text-primary',
};

export const NotificationBell = () => {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const { data: notifications = [] } = useQuery<Notification[]>({
    queryKey: ['myNotifications'],
    queryFn: async () => {
      const res = await axiosClient.get('/notifications/my');
      return res.data;
    },
    refetchInterval: 30000, // poll every 30s as a fallback
  });

  const unreadCount = notifications.filter(n => !n.isRead).length;

  const markReadMutation = useMutation({
    mutationFn: (id: number) => axiosClient.put(`/notifications/my/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['myNotifications'] }),
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => axiosClient.put('/notifications/my/read-all'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['myNotifications'] }),
  });

  // Listen to SSE for real-time notification events
  useEffect(() => {
    const baseUrl = (import.meta.env.VITE_API_BASE_URL as string || '').replace(/\/$/, '');
    const source = new EventSource(`${baseUrl}/api/v1/events/stream`, { withCredentials: true });
    source.addEventListener('new_notification', () => {
      queryClient.invalidateQueries({ queryKey: ['myNotifications'] });
      toast({ title: 'New Notification', description: 'You have a new notification.', variant: 'default' });
    });
    return () => source.close();
  }, [queryClient]);

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const formatTime = (dateStr: string) => {
    const date = new Date(dateStr);
    const diff = Date.now() - date.getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        id="notification-bell-btn"
        onClick={() => setOpen(o => !o)}
        className="relative p-2 text-on-surface-variant hover:text-primary transition-colors rounded-full hover:bg-surface-container-high"
        aria-label="Notifications"
      >
        <span className="material-symbols-outlined text-[24px]">notifications</span>
        {unreadCount > 0 && (
          <motion.span
            key={unreadCount}
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            className="absolute top-1 right-1 min-w-[18px] h-[18px] bg-primary text-on-primary text-[10px] font-bold rounded-full flex items-center justify-center border-2 border-background px-0.5"
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </motion.span>
        )}
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -8, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.97 }}
            transition={{ duration: 0.18 }}
            className="absolute right-0 top-full mt-2 w-[360px] max-h-[480px] overflow-hidden flex flex-col bg-surface-container-lowest border border-outline-variant/30 rounded-2xl shadow-2xl z-50"
          >
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-outline-variant/20 shrink-0">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">notifications</span>
                <span className="font-label-lg font-bold text-on-surface">Notifications</span>
                {unreadCount > 0 && (
                  <span className="px-2 py-0.5 bg-primary/10 text-primary text-[11px] font-bold rounded-full">
                    {unreadCount} new
                  </span>
                )}
              </div>
              {unreadCount > 0 && (
                <button
                  onClick={() => markAllReadMutation.mutate()}
                  className="text-[11px] text-primary hover:underline font-medium"
                >
                  Mark all read
                </button>
              )}
            </div>

            {/* List */}
            <div className="overflow-y-auto flex-1 divide-y divide-outline-variant/10">
              {notifications.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <span className="material-symbols-outlined text-outline/40 text-5xl mb-3">notifications_off</span>
                  <p className="text-on-surface-variant text-sm">You're all caught up!</p>
                </div>
              ) : (
                notifications.map(n => (
                  <button
                    key={n.id}
                    onClick={() => { if (!n.isRead) markReadMutation.mutate(n.id); }}
                    className={`w-full text-left flex items-start gap-3 px-4 py-3 hover:bg-surface-container-low transition-colors ${!n.isRead ? 'bg-primary/[0.03]' : ''}`}
                  >
                    <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${n.isRead ? 'bg-surface-container' : 'bg-primary/10'}`}>
                      <span className={`material-symbols-outlined text-[18px] ${typeColor[n.type] || 'text-primary'}`}>
                        {typeIcon[n.type] || 'notifications'}
                      </span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start justify-between gap-2">
                        <p className={`text-sm font-medium leading-snug ${n.isRead ? 'text-on-surface-variant' : 'text-on-surface'}`}>
                          {n.title}
                        </p>
                        {!n.isRead && <span className="w-2 h-2 rounded-full bg-primary shrink-0 mt-1" />}
                      </div>
                      <p className="text-[12px] text-on-surface-variant mt-0.5 line-clamp-2 leading-relaxed">{n.message}</p>
                      <p className="text-[11px] text-outline mt-1">{formatTime(n.createdAt)}</p>
                    </div>
                  </button>
                ))
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
