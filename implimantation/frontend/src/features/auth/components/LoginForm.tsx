import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useLoginMutation } from '../api';
import { useNavigate } from 'react-router-dom';
import { Mail, Lock, ShieldAlert, Loader2 } from 'lucide-react';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export function LoginForm() {
  const navigate = useNavigate();
  const [login, { isLoading, error }] = useLoginMutation();

  const { register, handleSubmit, formState: { errors } } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    try {
      const response = await login(data).unwrap();
      if (response.role === 'SYSTEM_ADMIN') {
        navigate('/admin/dashboard');
      } else if (response.tenantSlug) {
        navigate(`/${response.tenantSlug}/dashboard`);
      }
    } catch (err) {
      // Error handled by RTK Query
    }
  };

  return (
    <div className="w-full max-w-md p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100">
      <div className="flex flex-col items-center mb-8">
        <div className="p-3 bg-teal-50 text-teal-600 border border-teal-100 rounded-2xl mb-4">
          <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </div>
        <h2 className="text-2xl font-bold tracking-tight text-slate-800">Pracisos Portal</h2>
        <p className="text-slate-500 text-sm mt-1">Inspired by Jane App clinic layout</p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div>
          <label htmlFor="email" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Email Address</label>
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
              <Mail className="h-5 w-5" />
            </div>
            <input
              {...register('email')}
              type="email"
              id="email"
              placeholder="name@clinic.com"
              className="block w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
          </div>
          {errors.email && <p className="text-red-500 text-xs mt-1.5 flex items-center"><ShieldAlert className="w-3.5 h-3.5 mr-1" /> {errors.email.message}</p>}
        </div>

        <div>
          <label htmlFor="password" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Password</label>
          <div className="relative">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
              <Lock className="h-5 w-5" />
            </div>
            <input
              {...register('password')}
              type="password"
              id="password"
              placeholder="••••••••"
              className="block w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
            />
          </div>
          {errors.password && <p className="text-red-500 text-xs mt-1.5 flex items-center"><ShieldAlert className="w-3.5 h-3.5 mr-1" /> {errors.password.message}</p>}
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-100 rounded-xl flex items-start text-red-600 text-xs">
            <ShieldAlert className="w-4 h-4 mr-2 shrink-0" />
            <span>{(error as any).data?.message || 'Login failed. Please check your credentials.'}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={isLoading}
          className="w-full flex items-center justify-center py-3 px-4 bg-teal-600 hover:bg-teal-700 text-white font-semibold rounded-xl transition-all shadow-md shadow-teal-500/10 active:scale-[0.98] disabled:opacity-50"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-5 h-5 mr-2 animate-spin" />
              Signing in...
            </>
          ) : (
            'Sign In'
          )}
        </button>
      </form>
    </div>
  );
}
