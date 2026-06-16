import 'package:flutter/material.dart';
import 'package:client/core/theme/app_colors.dart';
import 'package:client/core/widgets/app_widgets.dart';

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              const Center(
                child: Column(
                  children: [
                    CircleAvatar(
                      radius: 50,
                      backgroundColor: AppColors.primaryContainer,
                      child: Icon(Icons.person_outline, size: 50, color: Colors.white),
                    ),
                    SizedBox(height: 16),
                    Text(
                      'Alex Commuter',
                      style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                    ),
                    Text(
                      'Free Plan • Active since April 2026',
                      style: TextStyle(color: AppColors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 40),
              
              const SectionHeader(title: 'Travel Statistics'),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(child: _StatCard(label: 'Trips', value: '42')),
                  const SizedBox(width: 12),
                  Expanded(child: _StatCard(label: 'CO2 Saved', value: '12kg')),
                  const SizedBox(width: 12),
                  Expanded(child: _StatCard(label: 'Saved', value: '14k')),
                ],
              ),
              
              const SizedBox(height: 32),
              const SectionHeader(title: 'Account Settings'),
              const SizedBox(height: 12),
              TonalCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    _MenuTile(icon: Icons.credit_card, title: 'Payment Methods', subtitle: 'Visa ends in 4421'),
                    const Divider(height: 1, indent: 56),
                    _MenuTile(icon: Icons.confirmation_number_outlined, title: 'My Tickets', subtitle: '3 active passes'),
                    const Divider(height: 1, indent: 56),
                    _MenuTile(icon: Icons.notifications_outlined, title: 'Notification Prefs', subtitle: 'Push, Email'),
                    const Divider(height: 1, indent: 56),
                    _MenuTile(icon: Icons.verified_user_outlined, title: 'Privacy & Security'),
                  ],
                ),
              ),
              const SizedBox(height: 32),
              TextButton(
                onPressed: () {},
                child: const Text('Log Out', style: TextStyle(color: AppColors.error, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;

  const _StatCard({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return TonalCard(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Column(
        children: [
          Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AppColors.primary)),
          Text(label, style: const TextStyle(fontSize: 12, color: AppColors.onSurfaceVariant)),
        ],
      ),
    );
  }
}

class _MenuTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;

  const _MenuTile({required this.icon, required this.title, this.subtitle});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: AppColors.onSurfaceVariant),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
      subtitle: subtitle != null ? Text(subtitle!) : null,
      trailing: const Icon(Icons.keyboard_arrow_right, size: 16),
      onTap: () {},
    );
  }
}
