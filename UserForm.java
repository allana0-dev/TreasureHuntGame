import javax.swing.JOptionPane;

public class UserForm {
    public static void main(String[] args) {
        // Prompt for Name
        String name = JOptionPane.showInputDialog("What is your name and surname:");
        
        // Prompt for Age
        String ageStr = JOptionPane.showInputDialog("Please enter your age:");
        int age = Integer.parseInt(ageStr);
        
        // Prompt for Address
        String address = JOptionPane.showInputDialog("Please enter your address:");
        
        // Prompt for Salary
        String salaryStr = JOptionPane.showInputDialog("Please enter your salary:");
        double salary = Double.parseDouble(salaryStr);
        
        // Calculate 5% reduction
        double reducedSalary = salary - (salary * 0.05);
        
        // Display Results
        String message = "Name: " + name + "\n" +
                         "Age: " + age + "\n" +
                         "Address: " + address + "\n" +
                         "Original Salary: $" + salary + "\n" +
                         "Salary after 5% reduction: $" + reducedSalary + "\n\n" +
                         "By Judee Jeremiah";

        JOptionPane.showMessageDialog(null, message, "User Information", JOptionPane.INFORMATION_MESSAGE);
    }
}

